package graphic;

import database.Database;
import database.PlayerDao;
import logic.Game;
import domain.Player;
import domain.Road;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class GraphicLoader {

    private Canvas underlay;
    private Game game;
    private Stage stage;
    private final int scalerY = 60;
    private final int scalerX = 54;
    private StackPane map = new StackPane();
    private Pane pane = new Pane();
    private GraphicsContext plotter;
    private ArrayList<Road> roads = new ArrayList<>();
    private PlayerDao playerDao;

    public GraphicLoader(Stage stage) throws ClassNotFoundException {
        this.stage = stage;
        this.underlay = new Canvas(13 * scalerX, 12 * scalerY);
        this.map.getChildren().addAll(underlay, pane);
        this.plotter = underlay.getGraphicsContext2D();
        this.playerDao = new PlayerDao(new Database("jdbc:sqlite:MiniCatanDatabase.db"));
    }

    public Parent refreshGameView() {
        BorderPane root = new BorderPane();
        root.setLeft(this.getPlayersView());
        root.setCenter(this.getMapView());

        return root;
    }

    public Parent getPlayersView() {
        VBox players = new VBox();

        Label playersAndResources = new Label("Pelaajat ja resurssit:");
        playersAndResources.setFont(Font.font(16));
        players.getChildren().add(playersAndResources);

        game.getPlayers().stream().forEach(p -> {
            players.getChildren().add(new Label(p.getStatus()));
        });

        Label victoryPoints = new Label("\nPelaajien voittopisteet:");
        victoryPoints.setFont(Font.font(16));
        players.getChildren().add(victoryPoints);

        game.getPlayers().stream().forEach(p -> {
            players.getChildren().add(new Label(p.getName() + ": " + p.getWinPoints()));
        });

        Button throwTurn = new Button("Next turn");

        throwTurn.setOnAction((event) -> {
            if (game.getTurn().realTurn()) {
                this.game.throwDice();
                this.game.nextTurn();
                this.stage.getScene().setRoot(this.refreshGameView());
            }
        });

        players.getChildren().addAll(new Label("\nTurn: " + this.game.getPlayerOnTurn().getName()), throwTurn);

        return players;
    }

    public Parent getMapView() {
        plotter.clearRect(0, 0, underlay.getWidth(), underlay.getHeight());
        pane.getChildren().clear();

        plotter.setFill(Color.BLACK);
        plotter.setStroke(Color.BLACK);
        plotter.setLineWidth(1);

        this.roads.stream().forEach(r -> {
            plotter.strokeLine(r.getLocation1().getX() * scalerX, r.getLocation1().getY() * scalerY,
                    r.getLocation2().getX() * scalerX, r.getLocation2().getY() * scalerY);
            int x = (r.getLocation1().getX() + r.getLocation2().getX()) * scalerX / 2 - 5;
            int y = (r.getLocation1().getY() + r.getLocation2().getY()) * scalerY / 2 - 5;
            plotter.fillOval(x, y, 10, 10);

            if (!this.game.getRoads().contains(r)) {
                Button b1 = new Button("");
                b1.setPrefSize(24, 24);
                b1.setBackground(Background.EMPTY);

                b1.setOnAction((event) -> {
                    this.game.clickRoad(r);
                    this.stage.getScene().setRoot(this.refreshGameView());
                    System.out.println("Tietä painettu.");
                });

                b1.setLayoutX((((double) r.getLocation1().getX() + r.getLocation2().getX()) / 2) * scalerX - 12);
                b1.setLayoutY((((double) r.getLocation1().getY() + r.getLocation2().getY()) / 2) * scalerY - 12);

                pane.getChildren().add(b1);
            }
        });

        game.getNodeWeb().getNodes().values().forEach(k -> {
            plotter.fillOval(k.getLocation().getX() * scalerX - 10, k.getLocation().getY() * scalerY - 10, 20, 20);

            Button b1 = new Button("");
            b1.setOnAction((event) -> {
                System.out.println(k.getId() + " clicked");
                this.game.clickNode(k);
                this.stage.getScene().setRoot(this.refreshGameView());
            });
            b1.setPrefSize(30, 30);
            b1.setBackground(Background.EMPTY);
            b1.setLayoutX((k.getLocation().getX() * scalerX) - 15);
            b1.setLayoutY((k.getLocation().getY() * scalerY) - 15);

            pane.getChildren().add(b1);
        });

        game.getNodeWeb().getNodes().keySet().stream().map(m -> this.game.getNodeWeb().getNode(m)).forEach(k -> {
            if (k.getBuilding() != null) {
                plotter.setFill(k.getBuilding().getPlayer().getColor());
                plotter.setStroke(k.getBuilding().getPlayer().getColor());
                plotter.fillOval(k.getLocation().getX() * scalerX - 10, k.getLocation().getY() * scalerY - 10, 20, 20);
                if (k.getBuilding().getValue() >= 2) {
                    plotter.strokeOval(k.getLocation().getX() * scalerX - 12, k.getLocation().getY() * scalerY - 12, 24, 24);
                }
                if (k.getBuilding().getValue() >= 3) {
                    plotter.strokeOval(k.getLocation().getX() * scalerX - 14, k.getLocation().getY() * scalerY - 14, 28, 28);
                }
            }
        });

        game.getFieldWeb().getFields().stream().forEach(f -> {
            plotter.setFill(Color.BLACK);
            plotter.setFont(Font.font(14));
            plotter.fillText(f.getValue() + ": " + f.getResource(), f.getLocation().getX() * scalerX, f.getLocation().getY() * scalerY);
        });

        game.getRoads().stream().forEach(r -> {
            plotter.setStroke(r.getPlayer().getColor());
            plotter.setLineWidth(6);
            plotter.strokeLine(r.getLocation1().getX() * scalerX, r.getLocation1().getY() * scalerY, r.getLocation2().getX() * scalerX, r.getLocation2().getY() * scalerY);
        });

        if (game.isEnded()) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Peli päättyi!");
            alert.setHeaderText(null);
            alert.setContentText("Pelin voitti: " + game.getPlayers().stream().sorted().findFirst().get().getName());
            alert.showAndWait();

            stage.setScene(new Scene(this.getMenuView()));
            stage.centerOnScreen();
        }

        return map;
    }

    public Parent getMenuView() {
        VBox buttons = new VBox();

        Button newGame = new Button("Uusi peli");
        Button statistics = new Button("Tilastoja");
        Button newPlayer = new Button("Luo uusi pelaaja");

        newGame.setPrefSize(200, 50);
        statistics.setPrefSize(200, 50);
        newPlayer.setPrefSize(200, 50);

        newGame.setOnAction((event) -> {
            try {
                this.getStartGameView();
            } catch (Exception e) {
                System.out.println("ERROR: " + e);
            }
        });

        newPlayer.setOnAction((event) -> {
            stage.setScene(new Scene(this.getNewPlayerView()));
        });

        buttons.getChildren().addAll(statistics, newPlayer, newGame);

        return buttons;
    }

    public void getStartGameView() {
        Dialog<List<Player>> dialog = new Dialog<>();
        VBox view = new VBox();
        view.setPrefHeight(200);

        List<Player> choices = null;
        try {
            choices = this.playerDao.findAll();
        } catch (Exception e) {
            System.out.println("Error: "+e);
        }
        ArrayList<Color> colors = new ArrayList(Arrays.asList(Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW));

        ChoiceBox choice = new ChoiceBox();

        choice.setPrefWidth(200);

        choices.stream().map(m -> m.getName()).forEach(p -> {
            choice.getItems().add(p);
        });

        view.getChildren().addAll(new Label("Lisää pelaaja: "), choice, new Label("Lisätyt pelaajat(vaaditaan 2-4): "));

        dialog.getDialogPane().setContent(view);

        ArrayList<Player> players = new ArrayList<>();

        choice.setOnAction((event) -> {
            if (players.size() < 4) {
                players.add(new Player(choice.getValue().toString(), colors.get(0)));
                colors.remove(0);
                view.getChildren().add(new Label(choice.getValue().toString()));
                choice.getItems().remove(choice.getValue());
            }
        });

        ButtonType cancelButton = new ButtonType("Peruuta", ButtonData.CANCEL_CLOSE);
        ButtonType startButton = new ButtonType("Aloita peli", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelButton, startButton);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == startButton && players.size() >= 2) {
                return players;
            }
            return null;
        });

        Optional<List<Player>> result = dialog.showAndWait();

        if (result.isPresent()) {
            this.game = new Game(players);
            game.getNodeWeb().getNodes().values().stream().forEach(n -> {
                n.getNeighbours().stream().forEach(k -> {
                    Road r = new Road(null, this.game.getNodeWeb().getNode(k), n);
                    if (!roads.contains(n)) {
                        roads.add(r);
                    }
                });
            });
            this.stage.setScene(new Scene(this.refreshGameView()));
            this.stage.centerOnScreen();
        }
    }

    public Parent getNewPlayerView() {
        VBox view = new VBox();

        Label newPlayer = new Label("Uuden pelaajan nimi(max 50 merkkiä):");
        TextField name = new TextField();
        Button add = new Button("Lisää pelaaja");

        add.setOnAction((event) -> {
            try {
                this.playerDao.add(new Player(name.getText(), null));
                this.stage.setScene(new Scene(this.getMenuView()));
            } catch (Exception e) {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.setTitle("ERROR");
                alert.setHeaderText(null);
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
            name.clear();
        });

        view.getChildren().addAll(newPlayer, name, add);

        return view;
    }

}