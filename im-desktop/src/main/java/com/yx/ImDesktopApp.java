package com.yx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ImDesktopApp extends Application {
    private static ImDesktopApp instance;

    public static ImDesktopApp getInstance() {
        return instance;
    }
    @Override
    public void start(Stage stage) throws Exception {
        instance = this;
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/login.fxml"));
        stage.setTitle("IM 登录");
        stage.setScene(new Scene(loader.load(), 320, 240));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}