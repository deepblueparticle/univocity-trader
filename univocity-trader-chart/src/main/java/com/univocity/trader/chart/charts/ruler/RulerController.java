package com.univocity.trader.chart.charts.ruler;

import com.univocity.trader.chart.annotation.Label;
import com.univocity.trader.chart.annotation.*;
import com.univocity.trader.chart.dynamic.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;

import static com.univocity.trader.chart.charts.ruler.DrawingProfile.Profile.*;

@UIBoundClass(updateProcessor = RulerUpdateProcessor.class)
public class RulerController<T> implements Controller, DrawingProfile {

	@Label("Background color")
	@ColorBound()
	private Color backgroundColor = new Color(255, 255, 255, 0);


	@Label("Grid color")
	@ColorBound()
	private Color gridColor = Color.LIGHT_GRAY;

	private Map<DrawingProfile.Profile, DrawingProfile> profiles;
	private DrawingProfile selectedProfile;

	@CheckBoxBound("Display grid")
	private boolean showingGrid = false;

	@Label("Font color")
	@ColorBound()
	private Color rulerFontColor = Color.BLACK;

	@Label("Selection color")
	@ColorBound()
	private Color selectionFontColor = Color.BLACK;

	private JPanel controlPanel;
	private Ruler<?> ruler;


	public RulerController(Ruler<?> ruler) {
		this.ruler = ruler;
		profiles = new EnumMap<DrawingProfile.Profile, DrawingProfile>(DrawingProfile.Profile.class);
		profiles.put(DEFAULT, new DrawingProfileImpl(new BasicStroke(1), Color.GRAY, new Font("Arial", Font.PLAIN, 10), Color.BLACK));
		profiles.put(SELECTION, new DrawingProfileImpl(new BasicStroke(3), Color.BLUE, new Font("Arial", Font.BOLD, 12), Color.BLUE));
		selectedProfile = profiles.get(DEFAULT);

	}

	public boolean isShowingGrid() {
		return showingGrid;
	}

	public void setShowingGrid(boolean showingGrid) {
		this.showingGrid = showingGrid;
	}

	public Color getGridColor() {
		return gridColor;
	}

	public void setGridColor(Color gridColor) {
		this.gridColor = gridColor;
	}

	public void setProfile(Profile profile) {
		this.selectedProfile = profiles.get(profile);
	}

	public void updateFontSize(Graphics2D g) {
		for (DrawingProfile p : profiles.values()) {
			p.updateFontSize(g);
		}
	}

	@Override
	public void drawing(Graphics2D g) {
		selectedProfile.drawing(g);
	}

	@Override
	public Font getFont() {
		return selectedProfile.getFont();
	}

	@Override
	public Color getFontColor() {
		return selectedProfile.getFontColor();
	}

	@Override
	public int getFontHeight() {
		return selectedProfile.getFontHeight();
	}

	@Override
	public Color getLineColor() {
		return selectedProfile.getLineColor();
	}

	@Override
	public Stroke getStroke() {
		return selectedProfile.getStroke();
	}

	@Override
	public void setFont(Font font) {
		selectedProfile.setFont(font);
	}

	@Override
	public void setFontColor(Color fontColor) {
		selectedProfile.setFontColor(fontColor);
	}

	@Override
	public void setLineColor(Color lineColor) {
		selectedProfile.setLineColor(lineColor);
	}

	@Override
	public void setStroke(Stroke stroke) {
		selectedProfile.setStroke(stroke);
	}

	@Override
	public void text(Graphics2D g) {
		selectedProfile.text(g);
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		this.backgroundColor = backgroundColor;
	}

	public Color getRulerFontColor() {
		return profiles.get(DEFAULT).getFontColor();
	}

	public void setRulerFontColor(Color rulerFontColor) {
		this.rulerFontColor = rulerFontColor;
		profiles.get(DEFAULT).setFontColor(rulerFontColor);
		profiles.get(DEFAULT).setLineColor(rulerFontColor);
	}

	public Color getSelectionFontColor() {
		return profiles.get(SELECTION).getFontColor();
	}

	public void setSelectionFontColor(Color selectionFontColor) {
		this.selectionFontColor = selectionFontColor;
		profiles.get(SELECTION).setFontColor(selectionFontColor);
		profiles.get(SELECTION).setLineColor(selectionFontColor);
	}

	@Override
	public int getStringWidth(String str, Graphics2D g) {
		return selectedProfile.getStringWidth(str, g);
	}

	public int getMaxStringWidth(String str, Graphics2D g) {
		int max = 0;
		for (DrawingProfile p : profiles.values()) {
			int width = p.getStringWidth(str, g);
			if (width > max) {
				max = width;
			}
		}
		return max;
	}

	public int centralizeYToFontHeight(int y) {
		return y - getFontHeight() / 2;
	}

	@Override
	public int getFontAscent(Graphics2D g) {
		return selectedProfile.getFontAscent(g);
	}

	@Override
	public JPanel getControlPanel() {
		if (controlPanel == null) {
			controlPanel = PanelBuilder.createPanel(this);
		}
		return controlPanel;
	}

	public Ruler<?> getRuler(){
		return ruler;
	}
}
