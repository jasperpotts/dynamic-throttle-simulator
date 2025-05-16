package com.hashgraph.dynamicthrottles.ui;

import javafx.beans.property.Property;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * A simple sparkline control that shows a line chart with a single series of data points. Along with a title, value
 * and unit labels.
 */
public class SparkLine extends VBox {
    private final Label valueLabel = new Label();
    private final AreaChart.Series<Number, Number> series = new AreaChart.Series<>();
    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();
    private int timeRangeInSeconds = 30;
    private long lastTime = System.currentTimeMillis();
    private long lastTimeUpdateText = System.currentTimeMillis();

    public SparkLine(String title, String unit) {
        this(title, unit, 260, 120);
    }
    public SparkLine(String title, String unit, int prefWidth, int prefHeight) {
        getStyleClass().add("sparkline");
        setPrefSize(prefWidth, prefHeight);
        setMinHeight(prefHeight);
        setSpacing(0);
        Label titleLabel = new Label();
        Label unitLabel = new Label();
        HBox labelsBox = new HBox(titleLabel, valueLabel, unitLabel);
        AreaChart<Number, Number> areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setMinHeight(40);
        getChildren().addAll(labelsBox, areaChart);
        labelsBox.getStyleClass().add("labelsBox");
        labelsBox.setAlignment(Pos.BASELINE_CENTER);
        labelsBox.setSpacing(10);
        titleLabel.getStyleClass().add("title");
        titleLabel.setText(title);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, javafx.scene.layout.Priority.ALWAYS);
        valueLabel.getStyleClass().add("value");
        valueLabel.setMinWidth(USE_PREF_SIZE);
        unitLabel.setText(unit);
        unitLabel.getStyleClass().add("unit");
        unitLabel.setMinWidth(USE_PREF_SIZE);

        xAxis.setLowerBound(-timeRangeInSeconds);
        xAxis.setUpperBound(0);
        xAxis.setAutoRanging(false);
        xAxis.setAnimated(false);
//        xAxis.setTickLabelsVisible(false);
        yAxis.setUpperBound(1d);
        yAxis.setAnimated(false);
        yAxis.setForceZeroInRange(true);
        areaChart.getData().add(series);
        areaChart.setHorizontalGridLinesVisible(true);
        areaChart.setVerticalGridLinesVisible(false);
        areaChart.setHorizontalZeroLineVisible(false);
        areaChart.setVerticalZeroLineVisible(false);
        areaChart.setCreateSymbols(false);
        areaChart.setLegendVisible(false);
    }

    public void setYAxis(double min, double max, double majorTickUnit) {
        yAxis.setLowerBound(min);
        yAxis.setUpperBound(max);
        yAxis.setTickUnit(majorTickUnit);
        yAxis.setAutoRanging(false);
        yAxis.setMinorTickVisible(false);
    }

    public void addValue(double value) {
        var data = series.getData();
        long time = System.currentTimeMillis();
        if ((time - lastTimeUpdateText) > 500) {
            lastTimeUpdateText = time;
            valueLabel.setText(String.format("%.2f", value));
        }
        long timeChange = time - lastTime;
        // rate limit changes to 4 per second
        if (timeChange < 250) return;
        double timeChangeInSeconds = (double)timeChange / 1000D;
        lastTime = time;
//        System.out.println("addValue value = "+value+"  timeChange = " + timeChange + "  timeChangeInSeconds = " + timeChangeInSeconds);
        // update yAxis range if needed
//        if (value > yAxis.getUpperBound()) {
//            yAxis.setUpperBound(Math.ceil(value));
//        }
        // add new data point, we have to add it first in the future so the animation is correct
        data.add(new LineChart.Data<>(timeChangeInSeconds, value));
        // move all existing data points back in time and remove those too old
        var iterator = data.iterator();
        while(iterator.hasNext()) {
            var entry = iterator.next();
            double entryNewTime = entry.getXValue().doubleValue() - timeChangeInSeconds;
            if (entryNewTime < -(timeRangeInSeconds*1.5)) {
                iterator.remove();
            } else {
                entry.setXValue(entryNewTime);
            }
        }
//        Platform.runLater(() -> {
//        });
//        data.add(new LineChart.Data<>(0, value));
//        System.out.println("data = " + data);
    }

    public int timeRangeInSeconds() {
        return timeRangeInSeconds;
    }

    public void timeRangeInSeconds(int timeRangeInSeconds) {
        this.timeRangeInSeconds = timeRangeInSeconds;
        xAxis.setLowerBound(-timeRangeInSeconds);
    }
}
