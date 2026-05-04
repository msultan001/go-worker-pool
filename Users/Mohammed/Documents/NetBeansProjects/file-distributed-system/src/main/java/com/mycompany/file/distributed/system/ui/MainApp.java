package com.mycompany.file.distributed.system.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

// start UI app
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(
                Objects.requireNonNull(getClass().getResource("/main.fxml"),
                        "Cannot find /main.fxml on classpath"));

        Scene scene = new Scene(root, 1000, 680);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles.css"),
                        "Cannot find /styles.css on classpath").toExternalForm());

        primaryStage.setTitle("File Distributed System — TCP Client");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(560);
        primaryStage.show();
    }

    // start main app
    public static void main(String[] args) {
        launch(args);
    }
}
