package com.hashgraph.dynamicthrottles.ui;

import static com.hashgraph.dynamicthrottles.DynamicMain.NUM_OF_NODES;
import static com.hashgraph.dynamicthrottles.DynamicMain.consensus;
import static com.hashgraph.dynamicthrottles.DynamicMain.loadGenerator;
import static com.hashgraph.dynamicthrottles.DynamicMain.nodes;
import static com.hashgraph.dynamicthrottles.Node.NANOS_PER_SECOND;

import com.hashgraph.dynamicthrottles.DynamicMain;
import com.hashgraph.dynamicthrottles.Node;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.chart.ChartData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class DynamicThrottlesUiApp extends Application {

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws IOException {
        // Load the FXML file for the main application window
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("main-frame.fxml"));
        Parent mainPanel = fxmlLoader.load();
        MainFrame mainFrameController = fxmlLoader.getController();
        Scene scene = new Scene(mainPanel, 1600, 1100);
        // load CSS
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("main.css")).toExternalForm());
        // setup and show stage
        stage.setTitle("Dynamic Throttles UI");
        stage.setScene(scene);
        stage.show();

        // setup accepted/rejected chart data
        final ChartData acceptedValueChartData = new ChartData("Accepted", 0, Tile.GREEN);
        final ChartData rejectedValueChartData = new ChartData("Rejected", 0, Tile.RED);
        mainFrameController.loadGeneratorAcceptRejectTile.setChartData(acceptedValueChartData, rejectedValueChartData);
        // setup chart data
        final List<ChartData> acceptedChartData = createChartData(mainFrameController.loadGeneratorAcceptedChartTile, Tile.GREEN, Tile.GREEN);
        final List<ChartData> rejectedChartData = createChartData(mainFrameController.loadGeneratorRejectedChartTile, Tile.RED, Tile.RED);
        final List<ChartData> tpsChartData = createChartData(mainFrameController.loadGeneratorTpsChartTile, null, Tile.BLUE);
        final List<ChartData> consensusQueueSizeChartData = createChartData(mainFrameController.consenusQueueSizeChartTile, null, Tile.BLUE);
        final List<ChartData> transactionWorkNsInLastSecondChartData = createChartData(mainFrameController.transactionWorkNsInLastSecondChartTile, null, Tile.BLUE);
        // create node panels
        List<NodeUi> nodeUis = IntStream.range(1,NUM_OF_NODES+1).mapToObj(i -> {
            try {
                return createNodeUi(mainFrameController.nodesContainer, i);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).toList();
        // create custom load slider panel
        Slider loadSlider = new Slider(0, 100, 0);
        loadSlider.setShowTickLabels(true);
        loadSlider.setShowTickMarks(true);
        loadSlider.setMajorTickUnit(10);
        loadSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            loadGenerator.percentageLargeTransactions.set(newValue.intValue());
        });
        mainFrameController.loadSliderTile.setGraphic(loadSlider);

        // start timer timeline to update the UI every second
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            final long tps = loadGenerator.getTransactionsSinceLastCall();
            mainFrameController.loadGeneratorTpsTile.setValue(tps);
            addChartData(mainFrameController.loadGeneratorTpsChartTile, tpsChartData, tps);
            final long[] acceptedAndRejected = loadGenerator.getAcceptedRejectedSinceLastCall();
            double total = acceptedAndRejected[0] + acceptedAndRejected[1];
            double acceptedPercentage = total == 0 ? 0 : ((double)acceptedAndRejected[0] / total)*100;
            double rejectedPercentage = total == 0 ? 0 : ((double)acceptedAndRejected[1] / total)*100;

            acceptedValueChartData.setValue(acceptedPercentage);
            rejectedValueChartData.setValue(rejectedPercentage);
            addChartData(mainFrameController.loadGeneratorAcceptedChartTile, acceptedChartData, acceptedPercentage);
            addChartData(mainFrameController.loadGeneratorRejectedChartTile, rejectedChartData, rejectedPercentage);

            addChartData(mainFrameController.consenusQueueSizeChartTile, consensusQueueSizeChartData, consensus.consensusQueueSize());

            mainFrameController.roundsLabel.setText("%,d".formatted(DynamicMain.roundsInLastSecond.get()));
            mainFrameController.eventsLabel.setText("%,d".formatted(DynamicMain.eventsInLastSecond.getAndSet(0)));
            mainFrameController.transactionLabel.setText("%,d".formatted(DynamicMain.transactionsInLastSecond.getAndSet(0)));
            mainFrameController.currentRoundLabel.setText("%,d".formatted(consensus.currentRound.get()));

            mainFrameController.quorumHealthSparkLine.addValue(nodes.getFirst().globalIntakeController.quorumHealth.get());
            mainFrameController.tokenRateSparkLine.addValue(nodes.getFirst().globalIntakeController.getCurrentTokenRate());

            addChartData(mainFrameController.transactionWorkNsInLastSecondChartTile, transactionWorkNsInLastSecondChartData, (double)DynamicMain.transactionWorkNsInLastSecond.getAndSet(0)/NANOS_PER_SECOND);

            // update nodes
            for (int i = 0; i < NUM_OF_NODES; i++) {
                Node node = nodes.get(i);
                NodeUi nodeUi = nodeUis.get(i);
                // update round number
                nodeUi.currentRoundLabel.setText("%,d".formatted(node.currentRound.get()));
                // update ingested transactions
                nodeUi.ingestedTransactionsLabel.setText("%,d".formatted(node.ingestedTransactions.get()));
                // update pid label
                nodeUi.pidLabel.setText(node.globalIntakeController.pid.toString());
                // update token rate
                nodeUi.currentTokenRateLabel.setText("%.1f".formatted(node.globalIntakeController.getCurrentTokenRate()));
                // update current tokens
                nodeUi.currentTokensLabel.setText("%.1f".formatted(node.globalIntakeController.getCurrentTokenCount()));
                nodeUi.healthChartTile.setTitle("Health");
                // update node queue size
                nodeUi.incomingTransactionQueueSparkLine.addValue(node.incomingTransactionQueue.size());
                // update roundsToExecuteQueue size
                nodeUi.roundsToExecuteQueueSparkLine.addValue(node.roundsToExecuteQueue.size());
                // update node health
                nodeUi.healthChartTile.setValue(node.healthPercentage.get());
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE); // Repeat indefinitely
        timeline.play();
        DynamicMain.startSimulation();
    }

    public NodeUi createNodeUi(HBox nodesPane, int nodeId) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("node.fxml"));
        Parent nodePanel = fxmlLoader.load();
        nodesPane.getChildren().add(nodePanel);
        NodeUi nodeController = fxmlLoader.getController();
        nodeController.nodeTitledPane.setText("Node "+nodeId);
        return nodeController;
    }

    public static void addChartData(Tile tile, List<ChartData> chartData, double value) {
        ChartData first = chartData.removeFirst();
        first.setValue(value);
        chartData.add(first);
        tile.setChartData(chartData);
    }

    public static List<ChartData> createChartData(Tile tile, Color textColor, Color barColor) {
        List<ChartData> chartData = new ArrayList<>();
        IntStream.range(0, 60).forEach(i -> chartData.add(new ChartData(0)));
        tile.setChartData(chartData);
        if (textColor != null) tile.setValueColor(textColor);
        tile.setBarColor(barColor);
        return chartData;
    }
}
