package com.univocity.trader.account;

import com.univocity.trader.*;
import com.univocity.trader.candles.*;
import com.univocity.trader.indicators.base.*;
import com.univocity.trader.notification.*;
import com.univocity.trader.simulation.*;
import com.univocity.trader.utils.*;
import org.apache.commons.lang3.*;
import org.slf4j.*;

import java.util.*;

import static com.univocity.trader.account.Order.Side.*;

public class TradingManager {

	private static final Logger log = LoggerFactory.getLogger(TradingManager.class);

	private static final long FIFTEEN_SECONDS = TimeInterval.seconds(15).ms;

	private final String symbol;
	final String assetSymbol;
	final String fundSymbol;
	private final AccountManager tradingAccount;
	protected Trader trader;
	private Exchange<?, ?> exchange;
	private final OrderListener[] notifications;
	private final ExchangeClient client;
	private OrderExecutionToEmail emailNotifier;
	private final SymbolPriceDetails priceDetails;
	private final SymbolPriceDetails referencePriceDetails;

	public TradingManager(Exchange exchange, SymbolPriceDetails priceDetails, AccountManager account, String assetSymbol, String fundSymbol, Parameters params) {
		if (exchange == null) {
			throw new IllegalArgumentException("Exchange implementation cannot be null");
		}
		if (priceDetails == null) {
			priceDetails = new SymbolPriceDetails(exchange);
		}
		if (account == null) {
			throw new IllegalArgumentException("Account manager cannot be null");
		}
		if (StringUtils.isBlank(assetSymbol)) {
			throw new IllegalArgumentException("Symbol of instrument to buy cannot be blank (examples: 'MSFT', 'BTC', 'EUR')");
		}
		if (StringUtils.isBlank(fundSymbol)) {
			throw new IllegalArgumentException("Currency cannot be blank (examples: 'USD', 'EUR', 'USDT', 'ETH')");
		}
		this.exchange = exchange;
		this.client = account.getClient();
		this.assetSymbol = assetSymbol;
		this.fundSymbol = fundSymbol;
		this.symbol = assetSymbol + fundSymbol;

		Instances<OrderListener> listenerProvider = client.getOrderListeners();
		this.notifications = listenerProvider != null ? listenerProvider.create(symbol, params) : new OrderListener[0];
		client.registerTradingManager(this);
		tradingAccount = client.getAccountManager();
		this.emailNotifier = getEmailNotifier();

		this.priceDetails = priceDetails.switchToSymbol(symbol);
		this.referencePriceDetails = priceDetails.switchToSymbol(getReferenceCurrencySymbol());
	}

	public SymbolPriceDetails getPriceDetails() {
		return priceDetails;
	}

	public SymbolPriceDetails getReferencePriceDetails() {
		return referencePriceDetails;
	}

	public String getFundSymbol() {
		return fundSymbol;
	}

	public String getAssetSymbol() {
		return assetSymbol;
	}

	public double getLatestPrice() {
		return getLatestPrice(assetSymbol, fundSymbol);
	}

	public Candle getLatestCandle() {
		return trader.latestCandle();
	}

	public double getLatestPrice(String assetSymbol, String fundSymbol) {
		Candle lastCandle = getLatestCandle();
		if (lastCandle != null && (System.currentTimeMillis() - lastCandle.closeTime) < FIFTEEN_SECONDS) {
			return lastCandle.close;
		}
		return exchange.getLatestPrice(assetSymbol, fundSymbol);
	}

	public String getSymbol() {
		return symbol;
	}

	public Map<String, Double> getAllPrices() {
		return exchange.getLatestPrices();
	}

	public boolean hasAssets(Candle c, boolean includeLocked) {
		double minimum = getPriceDetails().getMinimumOrderAmount(c.close);
		double positionValue = (includeLocked ? getTotalAssets() : getAssets()) * c.close;
		return positionValue > minimum && positionValue > minimumInvestmentAmountPerTrade();
	}

	double minimumInvestmentAmountPerTrade() {
		return getAccount().configuration().minimumInvestmentAmountPerTrade(assetSymbol);
	}

	public final Order buy(double quantity) {
		return tradingAccount.buy(assetSymbol, fundSymbol, quantity);
	}

//	public boolean switchTo(String ticker, Signal trade, String exitSymbol) {
//		String targetSymbol = exitSymbol + fundSymbol;
//		double targetUnitPrice = getLatestPrice(exitSymbol, fundSymbol);
//		if (targetUnitPrice <= 0.0) {
//			return false;
//		}
//
//		final Trader purchaseTrader = getTraderOf(targetSymbol);
//		if (trader != null) {
//			double quantityToSell = tradingAccount.allocateFunds(exitSymbol, assetSymbol);
//			double saleUnitPrice = trader.getLastClosingPrice();
//			double saleAmount = quantityToSell * saleUnitPrice;
//			double quantityToBuy = saleAmount / targetUnitPrice;
//
//			trader.setExitReason("Switching from " + ticker + " to " + targetSymbol);
//
//			if (trade == SELL) {
//				return processOrder(trader, tradingAccount.sell(assetSymbol, exitSymbol, quantityToSell));
//			} else {
//				return processOrder(purchaseTrader, tradingAccount.buy(exitSymbol, assetSymbol, quantityToBuy));
//			}
//		}
//		return false;
//	}

	public Order sell(double quantity) {
		if (quantity * getLatestPrice() < minimumInvestmentAmountPerTrade()) {
			return null;
		}
		return tradingAccount.sell(assetSymbol, fundSymbol, quantity);
	}

	public Order sell() {
		return sell(getAssets());
	}

	public double getAssets() {
		return tradingAccount.getAmount(assetSymbol);
	}

	public double getTotalAssets() {
		return tradingAccount.getBalance(assetSymbol).getTotal().doubleValue();
	}

	public double getCash() {
		return tradingAccount.getAmount(fundSymbol);
	}

	public double allocateFunds() {
		return tradingAccount.allocateFunds(assetSymbol);
	}

	public double getTotalFundsInReferenceCurrency() {
		return tradingAccount.getTotalFundsInReferenceCurrency();
	}

	public double getTotalFundsIn(String symbol) {
		return tradingAccount.getTotalFundsIn(symbol);
	}

	public boolean exitExistingPositions(String exitSymbol, Candle c) {
		boolean exited = false;
		for (TradingManager action : tradingAccount.getAllTradingManagers()) {
			if (action != this && action.hasAssets(c, false) && action.trader.switchTo(exitSymbol, c, action.symbol)) {
				exited = true;
				break;
			}
		}
		return exited;
	}

	public boolean waitingForBuyOrderToFill() {
		return tradingAccount.waitingForFill(assetSymbol, BUY);
	}

	public final Map<String, Balance> updateBalances() {
		return tradingAccount.updateBalances();

	}

	public String getReferenceCurrencySymbol() {
		return tradingAccount.getReferenceCurrencySymbol();
	}

	public Trader getTrader() {
		return this.trader;
	}

	public boolean isBuyLocked() {
		return tradingAccount.isBuyLocked(assetSymbol);
	}

	public AccountManager getAccount() {
		return tradingAccount;
	}

	public void cancelStaleOrdersFor(Trader trader) {
		tradingAccount.cancelStaleOrdersFor(trader);
	}

	public void cancelOrder(Order order) {
		tradingAccount.cancelOrder(order);
	}

	public TradingFees getTradingFees() {
		return tradingAccount.getTradingFees();
	}

	public void sendBalanceEmail(String title, ExchangeClient client) {
		getEmailNotifier().sendBalanceEmail(title, client);
	}

	public OrderExecutionToEmail getEmailNotifier() {
		if (emailNotifier == null) {
			for (int i = 0; i < notifications.length; i++) {
				if (notifications[i] instanceof OrderExecutionToEmail) {
					emailNotifier = (OrderExecutionToEmail) notifications[i];
					break;
				}
			}
			if (emailNotifier == null) {
				emailNotifier = new OrderExecutionToEmail();
			}
			emailNotifier.initialize(this);
		}
		return emailNotifier;
	}

	void notifyOrderSubmitted(Order order, Trade trade) {
		notifyOrderSubmitted(order, trade, this.notifications);
		notifyOrderSubmitted(order, trade, trader.notifications);
	}

	private void notifyOrderSubmitted(Order order, Trade trade, OrderListener[] notifications) {
		for (int i = 0; i < notifications.length; i++) {
			try {
				notifications[i].orderSubmitted(order, trade, client);
			} catch (Exception e) {
				log.error("Error sending orderSubmitted notification for order: " + order, e);
			}
		}
	}

	private void notifyOrderFinalized(Order order, Trade trade, OrderListener[] notifications) {
		for (int i = 0; i < notifications.length; i++) {
			try {
				notifications[i].orderFinalized(order, trade, client);
			} catch (Exception e) {
				log.error("Error sending orderFinalized notification for order: " + order, e);
			}
		}
	}

	void notifyOrderFinalized(Order order, Trade trade) {
		trader.orderFinalized(order);
		notifyOrderFinalized(order, trade, this.notifications);
		notifyOrderFinalized(order, trade, trader.notifications);
	}

	void notifySimulationEnd() {
		notifySimulationEnd(this.notifications);
		notifySimulationEnd(trader.notifications);
	}

	private void notifySimulationEnd(OrderListener[] notifications) {
		for (int i = 0; i < notifications.length; i++) {
			try {
				notifications[i].simulationEnded(trader, client);
			} catch (Exception e) {
				log.error("Error sending onSimulationEnd notification", e);
			}
		}
	}

	public void updateOpenOrders(String symbol, Candle candle) {
		if (tradingAccount.isSimulated()) {
			tradingAccount.updateOpenOrders(symbol, candle);
		}
	}

	public Balance getBalance(String symbol) {
		return tradingAccount.getBalance(symbol);
	}

//	boolean isDirectSwitchSupported(String currentAssetSymbol, String targetAssetSymbol) {
//		return exchange.isDirectSwitchSupported(currentAssetSymbol, targetAssetSymbol);
//	}
}
