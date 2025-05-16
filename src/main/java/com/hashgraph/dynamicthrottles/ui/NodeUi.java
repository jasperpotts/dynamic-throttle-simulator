package com.hashgraph.dynamicthrottles.ui;

import static com.hashgraph.dynamicthrottles.ui.DynamicThrottlesUiApp.createChartData;

import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.chart.ChartData;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;

public class NodeUi {
    @FXML TitledPane nodeTitledPane;
    @FXML Label currentRoundLabel;
    @FXML Label ingestedTransactionsLabel;
    @FXML Label pidLabel;
    @FXML Label currentTokensLabel;
    @FXML Label currentTokenRateLabel;

    @FXML Tile incomingTransactionQueueChartTile;
    @FXML Tile roundsToExecuteQueueChartTile;
    @FXML Tile healthChartTile;

    SparkLine incomingTransactionQueueSparkLine = new SparkLine("Incoming Queue", "Transactions");
    SparkLine roundsToExecuteQueueSparkLine = new SparkLine("Execute Queue", "Rounds");

    List<ChartData> incomingTransactionChartData;
    List<ChartData> roundsToExecuteChartData;

    @FXML
    public void initialize() {
        incomingTransactionChartData = createChartData(incomingTransactionQueueChartTile, null, Tile.BLUE);
        roundsToExecuteChartData = createChartData(roundsToExecuteQueueChartTile, null, Tile.BLUE);

        incomingTransactionQueueChartTile.setGraphic(incomingTransactionQueueSparkLine);
        roundsToExecuteQueueChartTile.setGraphic(roundsToExecuteQueueSparkLine);
    }
}
