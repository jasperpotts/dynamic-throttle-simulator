package com.hashgraph.dynamicthrottles.ui;

import static com.hashgraph.dynamicthrottles.ui.DynamicThrottlesUiApp.createChartData;

import eu.hansolo.tilesfx.Tile;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class MainFrame {

    @FXML
    HBox nodesContainer;

    @FXML Tile loadSliderTile;
    @FXML Tile loadGeneratorTpsTile;
    @FXML Tile loadGeneratorTpsChartTile;
    @FXML Tile loadGeneratorAcceptRejectTile;
    @FXML Tile loadGeneratorAcceptedChartTile;
    @FXML Tile loadGeneratorRejectedChartTile;

    @FXML Tile consenusQueueSizeChartTile;

    @FXML Label roundsLabel;
    @FXML Label eventsLabel;
    @FXML Label transactionLabel;
    @FXML Label currentRoundLabel;

    @FXML Tile transactionWorkNsInLastSecondChartTile;
    @FXML Tile quorumHealthTile;
    @FXML Tile tokenRateTile;

    SparkLine quorumHealthSparkLine = new SparkLine("Quorum Health", "%");
    SparkLine tokenRateSparkLine = new SparkLine("Token Rate", "TPS per Node");

    @FXML
    public void initialize() {
        quorumHealthTile.setGraphic(quorumHealthSparkLine);
        quorumHealthSparkLine.setYAxis(0,100, 20);
        quorumHealthSparkLine.timeRangeInSeconds(120);
        tokenRateTile.setGraphic(tokenRateSparkLine);
        tokenRateSparkLine.timeRangeInSeconds(120);
    }
}
