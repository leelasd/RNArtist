package io.github.fjossinet.rnartist;

import io.github.fjossinet.rnartist.gui.Canvas2D;
import io.github.fjossinet.rnartist.gui.RegisterDialog;
import io.github.fjossinet.rnartist.io.ChimeraDriver;
import io.github.fjossinet.rnartist.core.model.*;
import io.github.fjossinet.rnartist.core.model.io.Rnaview;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.commons.lang3.tuple.Pair;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;
import org.dizitart.no2.Document;
import org.dizitart.no2.NitriteId;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class RNArtist extends Application {

    private Mediator mediator;
    private Stage stage;
    private int scrollCounter = 0;
    private FlowPane statusBar;
    private Menu savedThemesMenu, currentThemeMenu, load2DForMenu;
    private MenuItem updateSavedThemeItem, clearAll2DsItem, clearAll2DsExceptCurrentItem;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        this.stage.setOnCloseRequest(windowEvent -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(RNArtist.this.stage);
            alert.initModality(Modality.WINDOW_MODAL);
            alert.setTitle("Confirm Exit");
            alert.setHeaderText(null);
            alert.setContentText("Are you sure to exit RNArtist?");

            Stage alerttStage = (Stage) alert.getDialogPane().getScene().getWindow();
            alerttStage.setAlwaysOnTop(true);
            alerttStage.toFront();

            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == ButtonType.OK) {
                try {
                    if (RnartistConfig.saveCurrentThemeOnExit())
                        RnartistConfig.save(mediator.getToolbox().getCurrentTheme().getParams(), (org.apache.commons.lang3.tuple.Pair<String, NitriteId>)currentThemeMenu.getUserData());
                    else
                        RnartistConfig.save(null, null);
                    Platform.exit();
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                windowEvent.consume();
            }
        });
        RnartistConfig.load();
        if (RnartistConfig.getUserID() == null)
            new RegisterDialog(this);
        this.mediator = new Mediator(this);
        RnartistConfig.save(null, null);

        Screen screen = Screen.getPrimary();
        BorderPane root = new BorderPane();

        //++++++ Menus
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        Menu _2DMenu = new Menu("2D");
        Menu themesMenu = new Menu("Themes");
        Menu windowsMenu = new Menu("Windows");

        menuBar.getMenus().addAll(fileMenu, _2DMenu, themesMenu, windowsMenu);

        //++++++++ Project Menu
        MenuItem newItem = new MenuItem("New/Open Project...");

        newItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                mediator.getProjectManager().getStage().show();
                mediator.getProjectManager().getStage().toFront();
            }
        });

        MenuItem saveasItem = new MenuItem("Save Project As...");

        saveasItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (mediator.getCanvas2D().getSecondaryStructureDrawing() != null) {
                    mediator.getWorkingSession().setScreen_capture(true);
                    mediator.getWorkingSession().setScreen_capture_area(new java.awt.geom.Rectangle2D.Double(mediator.getCanvas2D().getBounds().getCenterX() - 200, mediator.getCanvas2D().getBounds().getCenterY() - 100, 400.0, 200.0));
                    mediator.getCanvas2D().repaint();
                    TextInputDialog dialog = new TextInputDialog("My Project");
                    dialog.initModality(Modality.NONE);
                    dialog.setTitle("Project Saving");
                    dialog.setHeaderText("Fit your 2D preview into the black rectangle before saving.");
                    dialog.setContentText("Please enter your project name:");
                    Optional<String> projectName = dialog.showAndWait();
                    if (projectName.isPresent()) {
                        BufferedImage image = mediator.getCanvas2D().screenCapture(null);
                        if (image != null) {
                            NitriteId id = mediator.getEmbeddedDB().addProject(projectName.get().trim(), mediator.getCurrent2DDrawing());
                            File pngFile = new File(new File(new File(mediator.getEmbeddedDB().getRootDir(), "images"), "user"), id.toString() + ".png");
                            try {
                                ImageIO.write(image, "PNG", pngFile);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            mediator.getProjectManager().addProject(id, projectName.get().trim());
                        }
                        mediator.getWorkingSession().setScreen_capture(false);
                        mediator.getWorkingSession().setScreen_capture_area(null);
                        mediator.getCanvas2D().repaint();
                    } else {
                        mediator.getWorkingSession().setScreen_capture(false);
                        mediator.getWorkingSession().setScreen_capture_area(null);
                        mediator.getCanvas2D().repaint();
                    }
                }
            }
        });

        MenuItem saveItem = new MenuItem("Save Project");

        saveItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {

            }
        });

        Menu loadDataMenu = new Menu("Load Data from...");

        MenuItem filesMenuItem = new MenuItem("Files...");

        filesMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                FileChooser fileChooser = new FileChooser();
                File file = fileChooser.showOpenDialog(stage);
                if (file != null) {
                    fileChooser.setInitialDirectory(file.getParentFile());
                    javafx.concurrent.Task<Pair<List<SecondaryStructureDrawing>, Exception>> loadData = new javafx.concurrent.Task<Pair<List<SecondaryStructureDrawing>, Exception>>() {

                        @Override
                        protected Pair<List<SecondaryStructureDrawing>, Exception> call() {
                            SecondaryStructure ss = null;
                            List<SecondaryStructureDrawing> secondaryStructureDrawings = new ArrayList<SecondaryStructureDrawing>();
                            try {
                                if (file.getName().endsWith(".ct")) {
                                    ss = io.github.fjossinet.rnartist.core.model.io.ParsersKt.parseCT(new FileReader(file));
                                    if (ss != null) {
                                        ss.getRna().setSource(file.getName());
                                        secondaryStructureDrawings.add(new SecondaryStructureDrawing(ss, mediator.getCanvas2D().getBounds(), mediator.getToolbox().getCurrentTheme(), new WorkingSession()));
                                    }
                                } else if (file.getName().endsWith(".bpseq")) {
                                    ss = io.github.fjossinet.rnartist.core.model.io.ParsersKt.parseBPSeq(new FileReader(file));
                                    if (ss != null) {
                                        ss.getRna().setSource(file.getName());
                                        secondaryStructureDrawings.add(new SecondaryStructureDrawing(ss, mediator.getCanvas2D().getBounds(), mediator.getToolbox().getCurrentTheme(), new WorkingSession()));
                                    }
                                } else if (file.getName().endsWith(".fasta") || file.getName().endsWith(".fas") || file.getName().endsWith(".vienna")) {
                                    ss = io.github.fjossinet.rnartist.core.model.io.ParsersKt.parseVienna(new FileReader(file));
                                    if (ss != null) {
                                        ss.getRna().setSource(file.getName());
                                        secondaryStructureDrawings.add(new SecondaryStructureDrawing(ss, mediator.getCanvas2D().getBounds(), mediator.getToolbox().getCurrentTheme(), new WorkingSession()));
                                    }

                                } else if (file.getName().endsWith(".pdb")) {
                                    int countBefore = secondaryStructureDrawings.size();
                                    for (TertiaryStructure ts : io.github.fjossinet.rnartist.core.model.io.ParsersKt.parsePDB(new FileReader(file))) {
                                        try {
                                            ss = new Rnaview().annotate(ts);
                                            if (ss != null) {
                                                ss.getRna().setSource(file.getName());
                                                ss.setTertiaryStructure(ts);
                                                secondaryStructureDrawings.add(new SecondaryStructureDrawing(ss, mediator.getCanvas2D().getBounds(), mediator.getToolbox().getCurrentTheme(), new WorkingSession()));
                                            }
                                        } catch (FileNotFoundException exception) {
                                            //do nothing, RNAVIEW can have problem to annotate some RNA (no 2D for example)
                                        }
                                    }
                                    if (countBefore < secondaryStructureDrawings.size()) {//RNAVIEW was able to annotate at least one RNA molecule
                                        if (mediator.getChimeraDriver() != null)
                                            mediator.getChimeraDriver().loadTertiaryStructure(file);
                                    } else { //we generate an Exception to show the Alert dialog
                                        throw new Exception("RNAVIEW was not able to annotate the 3D structure stored in " + file.getName());
                                    }

                                } else if (file.getName().endsWith(".stk") || file.getName().endsWith(".stockholm")) {
                                    for (SecondaryStructure _ss : io.github.fjossinet.rnartist.core.model.io.ParsersKt.parseStockholm(new FileReader(file))) {
                                        _ss.getRna().setSource(file.getName());
                                        secondaryStructureDrawings.add(new SecondaryStructureDrawing(_ss, mediator.getCanvas2D().getBounds(), mediator.getToolbox().getCurrentTheme(), new WorkingSession()));
                                    }
                                }
                            } catch (Exception e) {
                                return Pair.of(secondaryStructureDrawings, e);
                            }
                            return Pair.of(secondaryStructureDrawings, null);
                        }
                    };
                    loadData.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                        @Override
                        public void handle(WorkerStateEvent workerStateEvent) {
                            try {
                                if (loadData.get().getRight() != null) {
                                    Alert alert = new Alert(Alert.AlertType.ERROR);
                                    alert.setTitle("File Parsing error");
                                    alert.setHeaderText(loadData.get().getRight().getMessage());
                                    alert.setContentText("If this problem persists, you can send the exception stacktrace below to fjossinet@gmail.com");
                                    StringWriter sw = new StringWriter();
                                    PrintWriter pw = new PrintWriter(sw);
                                    loadData.get().getRight().printStackTrace(pw);
                                    String exceptionText = sw.toString();

                                    Label label = new Label("The exception stacktrace was:");

                                    TextArea textArea = new TextArea(exceptionText);
                                    textArea.setEditable(false);
                                    textArea.setWrapText(true);

                                    textArea.setMaxWidth(Double.MAX_VALUE);
                                    textArea.setMaxHeight(Double.MAX_VALUE);
                                    GridPane.setVgrow(textArea, Priority.ALWAYS);
                                    GridPane.setHgrow(textArea, Priority.ALWAYS);

                                    GridPane expContent = new GridPane();
                                    expContent.setMaxWidth(Double.MAX_VALUE);
                                    expContent.add(label, 0, 0);
                                    expContent.add(textArea, 0, 1);
                                    alert.getDialogPane().setExpandableContent(expContent);
                                    alert.showAndWait();
                                } else {
                                    for (SecondaryStructureDrawing drawing : loadData.get().getLeft())
                                        mediator.get_2DDrawingsLoaded().add(drawing);
                                    //we load and fit on the last 2D loaded
                                    mediator.canvas2D.load2D(mediator.get_2DDrawingsLoaded().get(mediator.get_2DDrawingsLoaded().size() - 1));
                                    mediator.canvas2D.fitDisplayOn(mediator.getCurrent2DDrawing().getBounds());
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    new Thread(loadData).start();

                }
            }
        });

        MenuItem databasesMenuItem = new MenuItem("Databases...");

        databasesMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {

            }
        });

        MenuItem scratchMenuItem = new MenuItem("Scratch");

        scratchMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {

            }
        });

        loadDataMenu.getItems().addAll(filesMenuItem, databasesMenuItem, scratchMenuItem);

        Menu exportMenu = new Menu("Export 2D As...");

        MenuItem exportSVGMenuItem = new MenuItem("SVG...");

        exportSVGMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SVG Files", "*.svg"));
                File file = fileChooser.showSaveDialog(stage);
                if (file != null) {
                    fileChooser.setInitialDirectory(file.getParentFile());
                    PrintWriter writer;
                    try {
                        writer = new PrintWriter(file);
                        writer.println(mediator.getCurrent2DDrawing().asSVG());
                        writer.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        MenuItem exportCTMenuItem = new MenuItem("CT...");

        exportCTMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {

            }
        });

        MenuItem exportViennaMenuItem = new MenuItem("Vienna...");

        exportViennaMenuItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {

            }
        });

        exportMenu.getItems().addAll(exportSVGMenuItem, exportCTMenuItem, exportViennaMenuItem);

        fileMenu.getItems().addAll(newItem, loadDataMenu, saveasItem, saveItem, exportMenu);

        //++++++++ 2D Menu

        this.load2DForMenu = new Menu("Load...");

        this.clearAll2DsItem = new MenuItem("Clear All");
        this.clearAll2DsItem.setDisable(true);
        clearAll2DsItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText(null);
                alert.setContentText("Are you Sure to Remove all the 2Ds from your Project?");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() == ButtonType.OK)
                    mediator.get_2DDrawingsLoaded().clear();
            }
        });

        this.clearAll2DsExceptCurrentItem = new MenuItem("Clear All Except Current");
        this.clearAll2DsExceptCurrentItem.setDisable(true);
        clearAll2DsExceptCurrentItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText(null);
                alert.setContentText("Are you Sure to Remove all the Remaining 2Ds from your Project?");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() == ButtonType.OK) {
                    List<SecondaryStructureDrawing> toDelete = new ArrayList<SecondaryStructureDrawing>();
                    for (SecondaryStructureDrawing drawing : mediator.get_2DDrawingsLoaded())
                        if (drawing != mediator.getCurrent2DDrawing())
                            toDelete.add(drawing);
                    mediator.get_2DDrawingsLoaded().removeAll(toDelete);
                }
            }
        });

        this.load2DForMenu.getItems().addAll(clearAll2DsItem, clearAll2DsExceptCurrentItem, new SeparatorMenuItem());


        CheckMenuItem centerOnSelectionItem = new CheckMenuItem("Automatic Center on Selection");
        centerOnSelectionItem.setSelected(RnartistConfig.getCenterDisplayOnSelection());
        centerOnSelectionItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                RnartistConfig.setCenterDisplayOnSelection(centerOnSelectionItem.isSelected());
            }
        });

        CheckMenuItem fitOnSelectionItem = new CheckMenuItem("Automatic Fit on Selection");
        fitOnSelectionItem.setSelected(RnartistConfig.getFitDisplayOnSelection());
        fitOnSelectionItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                RnartistConfig.setFitDisplayOnSelection(fitOnSelectionItem.isSelected());
            }
        });

        _2DMenu.getItems().addAll(load2DForMenu, centerOnSelectionItem, fitOnSelectionItem);

        //++++++++ Themes Menu

        Menu defaultThemesMenu = new Menu("Default Themes");

        Menu userThemesMenu = new Menu("Your Themes");
        this.currentThemeMenu = new Menu("Current Theme");

        if (RnartistConfig.getLastThemeSavedId() != null) {
            this.currentThemeMenu.setUserData(RnartistConfig.getLastThemeSavedId());
            this.currentThemeMenu.setText(RnartistConfig.getLastThemeSavedId().getKey());
        }

        MenuItem saveAsCurrentTheme = new MenuItem("Save Current Theme As...");

        saveAsCurrentTheme.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Theme Saving");
                dialog.setHeaderText("Choose a Name for your Theme");

                Optional<String> response = dialog.showAndWait();

                response.ifPresent(name -> {
                    NitriteId id = mediator.getEmbeddedDB().addTheme(name, mediator.getToolbox().getCurrentTheme());
                    if (id != null) {
                        org.apache.commons.lang3.tuple.Pair<String, NitriteId> theme = org.apache.commons.lang3.tuple.Pair.of(name, id);
                        savedThemesMenu.getItems().add(createSavedThemeItem(theme));
                        updateSavedThemeItem.setUserData(theme); //to have the theme reference to update it for the user
                        updateSavedThemeItem.setDisable(false);
                        currentThemeMenu.setText(theme.getKey());
                        currentThemeMenu.setUserData(theme);
                        currentThemeMenu.setDisable(false);
                    }
                });
            }
        });

        this.updateSavedThemeItem = new MenuItem("Save");
        if (RnartistConfig.getLastThemeSavedId() != null)
            this.updateSavedThemeItem.setUserData(RnartistConfig.getLastThemeSavedId());
        else
            this.updateSavedThemeItem.setDisable(true);
        updateSavedThemeItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirmation Dialog");
                alert.setHeaderText(null);
                alert.setContentText("Are you Sure to Update your Theme?");

                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() == ButtonType.OK) {
                    org.apache.commons.lang3.tuple.Pair<String, NitriteId> theme = (org.apache.commons.lang3.tuple.Pair<String, NitriteId>) updateSavedThemeItem.getUserData();
                    mediator.getEmbeddedDB().updateTheme(theme.getValue(), mediator.getToolbox().getCurrentTheme().getParams());

                }
            }
        });

        currentThemeMenu.getItems().addAll(updateSavedThemeItem, saveAsCurrentTheme);

        this.savedThemesMenu = new Menu("Saved Themes");

        for (Document doc : mediator.getEmbeddedDB().getThemes().find())
            savedThemesMenu.getItems().add(createSavedThemeItem(org.apache.commons.lang3.tuple.Pair.of((String) doc.get("name"), doc.getId())));

        userThemesMenu.getItems().addAll(currentThemeMenu, this.savedThemesMenu);

        Menu communityThemesMenu = new Menu("Community Themes");

        themesMenu.getItems().addAll(defaultThemesMenu, userThemesMenu, communityThemesMenu);

        //++++++++ Windows Menu
        MenuItem toolboxItem = new MenuItem("Toolbox");

        toolboxItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                mediator.getToolbox().getStage().show();
                mediator.getToolbox().getStage().toFront();
            }
        });

        MenuItem explorerItem = new MenuItem("Explorer");

        explorerItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                mediator.getExplorer().getStage().show();
                mediator.getExplorer().getStage().toFront();
            }
        });

        MenuItem chimeraItem = new MenuItem("Chimera");

        chimeraItem.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (mediator.getChimeraDriver() != null) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Please Confirm");
                    alert.setHeaderText("A Chimera windows is already linked to RNArtist");
                    alert.setContentText("The new one will replace it for the linkage");
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get().equals(ButtonType.OK)) {
                        new ChimeraDriver(mediator);
                    }
                } else
                    new ChimeraDriver(mediator);
            }
        });

        windowsMenu.getItems().addAll(toolboxItem, explorerItem, chimeraItem);

        //++++++ Toolbar

        ToolBar toolbar = new ToolBar();

        toolbar.setPadding(new Insets(5,10,10,10));

        GridPane allButtons = new GridPane();
        allButtons.setVgap(5.0);
        allButtons.setHgap(5.0);

        Label l = new Label("2D");
        GridPane.setConstraints(l, 0, 0);
        allButtons.getChildren().add(l);

        Button center2D = new Button(null, new Glyph("FontAwesome", FontAwesome.Glyph.CROSSHAIRS));
        center2D.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (mediator.getCurrent2DDrawing() != null) {
                    mediator.getCanvas2D().centerDisplayOn(mediator.getCurrent2DDrawing().getBounds());
                }
            }
        });
        center2D.setTooltip(new Tooltip("Center 2D"));
        GridPane.setConstraints(center2D, 1, 0);
        allButtons.getChildren().add(center2D);

        Button fit2D = new Button(null, new Glyph("FontAwesome", FontAwesome.Glyph.ARROWS_ALT));
        fit2D.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (mediator.getCurrent2DDrawing() != null) {
                    mediator.getCanvas2D().fitDisplayOn(mediator.getCurrent2DDrawing().getBounds());
                }
            }
        });
        fit2D.setTooltip(new Tooltip("Fit 2D"));
        GridPane.setConstraints(fit2D, 2, 0);
        allButtons.getChildren().add(fit2D);

        l = new Label("3D");
        GridPane.setConstraints(l, 0, 1);
        allButtons.getChildren().add(l);

        Button set3DPivot = new Button(null, new Glyph("FontAwesome", FontAwesome.Glyph.UNDO));
        set3DPivot.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (mediator.getCurrent2DDrawing() != null && mediator.getChimeraDriver() != null)
                    mediator.pivotInChimera();
            }
        });
        set3DPivot.setTooltip(new Tooltip("Define Selection as Pivot"));
        GridPane.setConstraints(set3DPivot, 1, 1);
        allButtons.getChildren().add(set3DPivot);

        Button focus3D = new Button(null, new Glyph("FontAwesome", FontAwesome.Glyph.EYE));
        focus3D.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (mediator.getCurrent2DDrawing() != null && mediator.getChimeraDriver() != null)
                    mediator.focusInChimera();
            }
        });
        focus3D.setTooltip(new Tooltip("Focus 3D on Selection"));
        GridPane.setConstraints(focus3D, 2, 1);
        allButtons.getChildren().add(focus3D);

        GridPane residueOpacity = new GridPane();
        residueOpacity.setVgap(5.0);
        residueOpacity.setHgap(5.0);

        l = new Label("Unselected Residues Opacity (%)");
        GridPane.setHalignment(l, HPos.CENTER);
        GridPane.setConstraints(l, 0, 0);
        residueOpacity.getChildren().add(l);

        final Slider slider = new Slider(0, 100, (int) (RnartistConfig.getSelectionFading() / 255.0 * 100.0));
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(50);
        slider.setMinorTickCount(5);
        slider.setShowTickMarks(true);
        slider.setOnMouseReleased(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                RnartistConfig.setSelectionFading((int) (slider.getValue() / 100.0 * 255.0));
                mediator.getCanvas2D().repaint();
            }
        });
        GridPane.setHalignment(slider, HPos.CENTER);
        GridPane.setConstraints(slider, 0, 1);
        residueOpacity.getChildren().add(slider);

        Separator s = new Separator();
        s.setPadding(new Insets(0,5,0,5));

        toolbar.getItems().addAll(allButtons, s , residueOpacity);

        VBox vbox = new VBox();
        vbox.setPadding(new Insets(0, 0, 0, 0));
        vbox.setSpacing(5);
        vbox.getChildren().add(menuBar);
        vbox.getChildren().add(toolbar);

        root.setTop(vbox);

        //++++++ Canvas2D

        final SwingNode swingNode = new SwingNode();
        swingNode.setOnMouseClicked(mouseEvent -> {
            if (mediator.getCurrent2DDrawing() != null) {
                AffineTransform at = new AffineTransform();
                at.translate(mediator.getWorkingSession().getViewX(), mediator.getWorkingSession().getViewY());
                at.scale(mediator.getWorkingSession().getFinalZoomLevel(), mediator.getWorkingSession().getFinalZoomLevel());
                for (JunctionCircle jc : mediator.getCurrent2DDrawing().getAllJunctions()) {
                    if (at.createTransformedShape(jc.circle).contains(mouseEvent.getX(), mouseEvent.getY())) {
                        mediator.addToSelection(Mediator.SelectionEmitter.CANVAS2D, false, jc);
                        mediator.getCanvas2D().repaint();
                        return;
                    }
                }

                List<ResidueCircle> residues = mediator.getCurrent2DDrawing().getResidues();
                for (ResidueCircle c : residues) {
                    if (c.getCircle() != null && at.createTransformedShape(c.getCircle()).contains(mouseEvent.getX(), mouseEvent.getY())) {
                        mediator.addToSelection(Mediator.SelectionEmitter.CANVAS2D, false, c);
                        mediator.getCanvas2D().repaint();
                        return;
                    }
                }
                //no hit found. Ctrl or Alt has to be down to confirm unselection
                if (mouseEvent.isControlDown() || mouseEvent.isAltDown())
                    mediator.addToSelection(Mediator.SelectionEmitter.CANVAS2D, true, null);
                mediator.getCanvas2D().repaint();
            }
        });
        swingNode.setOnMouseDragged(mouseEvent -> {
            if (mediator.getCanvas2D().getSecondaryStructureDrawing() != null) {
                mediator.getTheme().setQuickDraw(true);
                double transX = mouseEvent.getX() - mediator.getCanvas2D().getTranslateX();
                double transY = mouseEvent.getY() - mediator.getCanvas2D().getTranslateY();
                mediator.getWorkingSession().moveView(transX, transY);
                mediator.getCanvas2D().setTranslateX(mouseEvent.getX());
                mediator.getCanvas2D().setTranslateY(mouseEvent.getY());
                mediator.getCanvas2D().repaint();
            }
        });
        swingNode.setOnMouseReleased(mouseEvent -> {
            if (mediator.getCanvas2D().getSecondaryStructureDrawing() != null) {
                mediator.getTheme().setQuickDraw(false);
                mediator.getCanvas2D().setTranslateX(0.0);
                mediator.getCanvas2D().setTranslateY(0.0);
                mediator.getCanvas2D().repaint();
            }
        });
        swingNode.setOnMousePressed(mouseEvent -> {
            if (mediator.getCanvas2D().getSecondaryStructureDrawing() != null) {
                mediator.getCanvas2D().setTranslateX(mouseEvent.getX());
                mediator.getCanvas2D().setTranslateY(mouseEvent.getY());
            }
        });
        swingNode.setOnScroll(scrollEvent -> {
            if (mediator.getCanvas2D().getSecondaryStructureDrawing() != null) {
                mediator.getTheme().setQuickDraw(true);
                scrollCounter++;

                Thread th = new Thread(() -> {
                    try {
                        Thread.sleep(100);
                        if (scrollCounter == 1) {
                            mediator.getTheme().setQuickDraw(false);
                            mediator.getCanvas2D().repaint();
                        }
                        scrollCounter--;
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                });
                th.setDaemon(true);
                th.start();
                Point2D.Double realMouse = new Point2D.Double(((double) scrollEvent.getX() - mediator.getWorkingSession().getViewX()) / mediator.getWorkingSession().getFinalZoomLevel(), ((double) scrollEvent.getY() - mediator.getWorkingSession().getViewY()) / mediator.getWorkingSession().getFinalZoomLevel());
                double notches = scrollEvent.getDeltaY();
                if (notches < 0)
                    mediator.getWorkingSession().setZoom(1.25);
                if (notches > 0)
                    mediator.getWorkingSession().setZoom(1.0 / 1.25);
                Point2D.Double newRealMouse = new Point2D.Double(((double) scrollEvent.getX() - mediator.getWorkingSession().getViewX()) / mediator.getWorkingSession().getFinalZoomLevel(), ((double) scrollEvent.getY() - mediator.getWorkingSession().getViewY()) / mediator.getWorkingSession().getFinalZoomLevel());
                mediator.getWorkingSession().moveView((newRealMouse.getX() - realMouse.getX()) * mediator.getWorkingSession().getFinalZoomLevel(), (newRealMouse.getY() - realMouse.getY()) * mediator.getWorkingSession().getFinalZoomLevel());
                mediator.getCanvas2D().repaint();
            }
        });
        createSwingContent(swingNode);
        root.setCenter(swingNode);

        //### Status Bar
        this.statusBar = new FlowPane();
        statusBar.setAlignment(Pos.CENTER_RIGHT);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setHgap(10);

        Label release = new Label(RnartistConfig.getRnartistRelease());
        statusBar.getChildren().add(release);

        Button twitter = new Button("Follow Us", new Glyph("FontAwesome", FontAwesome.Glyph.TWITTER));
        twitter.setOnAction(actionEvent -> {
            this.getHostServices().showDocument("https://twitter.com/rnartist_app");
        });
        statusBar.getChildren().add(twitter);

        root.setBottom(statusBar);
        Scene scene = new Scene(root, screen.getBounds().getWidth(), screen.getBounds().getHeight());
        stage.setScene(scene);
        stage.setTitle("RNArtist");

        Rectangle2D screenSize = Screen.getPrimary().getBounds();
        this.stage.setWidth(screenSize.getWidth() - 700);
        this.stage.setHeight(screenSize.getHeight());
        this.stage.setX(350);
        this.stage.setY(0);

        mediator.getProjectManager().getStage().show();
        mediator.getProjectManager().getStage().toFront();
    }

    private Menu createSavedThemeItem(org.apache.commons.lang3.tuple.Pair<String, NitriteId> theme) {
        Menu savedThemeMenu = new Menu(theme.getKey());

        MenuItem loadSavedTheme = new MenuItem("Load");
        loadSavedTheme.setUserData(theme);
        loadSavedTheme.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                org.apache.commons.lang3.tuple.Pair<String, NitriteId> theme = (org.apache.commons.lang3.tuple.Pair<String, NitriteId>) loadSavedTheme.getUserData();
                //we mute the listeners to avoid to apply the saved theme automatically
                try {
                    mediator.getToolbox().setMuted(true);
                    mediator.getToolbox().loadTheme(mediator.getEmbeddedDB().getTheme(theme.getValue()));
                    mediator.getToolbox().setMuted(false);
                    updateSavedThemeItem.setUserData(theme); //to have the theme reference to update it for the user
                    updateSavedThemeItem.setDisable(false);
                    currentThemeMenu.setUserData(theme);
                    currentThemeMenu.setText(theme.getKey());
                    currentThemeMenu.setDisable(false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        MenuItem deleteSavedTheme = new MenuItem("Delete");
        deleteSavedTheme.setUserData(theme);
        deleteSavedTheme.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                org.apache.commons.lang3.tuple.Pair<String, NitriteId> theme = (org.apache.commons.lang3.tuple.Pair<String, NitriteId>) deleteSavedTheme.getUserData();
                mediator.getEmbeddedDB().deleteTheme(theme.getValue());
                savedThemesMenu.getItems().remove(savedThemeMenu);
                if (currentThemeMenu.getUserData().equals(deleteSavedTheme.getUserData())) {
                    updateSavedThemeItem.setDisable(true);
                    currentThemeMenu.setText("Current Theme");
                    currentThemeMenu.setUserData(null);
                }
            }
        });

        MenuItem shareCurrentTheme = new MenuItem("Share...");
        shareCurrentTheme.setDisable(true);

        savedThemeMenu.getItems().addAll(loadSavedTheme, deleteSavedTheme, shareCurrentTheme);
        return savedThemeMenu;
    }

    public Menu getCurrentThemeMenu() {
        return currentThemeMenu;
    }

    public MenuItem getUpdateSavedThemeItem() {
        return updateSavedThemeItem;
    }

    public MenuItem getClearAll2DsItem() {
        return clearAll2DsItem;
    }

    public MenuItem getClearAll2DsExceptCurrentItem() {
        return clearAll2DsExceptCurrentItem;
    }

    public Menu getLoad2DForMenu() {
        return load2DForMenu;
    }

    public void showStatusBar(boolean show) {
        if (show)
            ((BorderPane) stage.getScene().getRoot()).setBottom(this.statusBar);
        else
            ((BorderPane) stage.getScene().getRoot()).setBottom(null);
    }

    public Stage getStage() {
        return stage;
    }

    private void createSwingContent(final SwingNode swingNode) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Canvas2D canvas = new Canvas2D(mediator);
                swingNode.setContent(canvas);
            }
        });
    }

    private abstract class Option {

        protected String title;

        protected Option(String title) {
            this.title = title;
        }

        abstract protected void check(boolean check);

        abstract protected boolean isChecked();

        @Override
        public String toString() {
            return this.title;
        }
    }

    private class DisplayTertiariesInSelection extends Option {

        public DisplayTertiariesInSelection() {
            super("Display Tertiary Interactions for Selection");
        }

        @Override
        protected boolean isChecked() {
            return RnartistConfig.getDisplayTertiariesInSelection();
        }

        @Override
        protected void check(boolean check) {
            RnartistConfig.setDisplayTertiariesInSelection(check);
        }
    }

    private class CenterDisplayOnSelection extends Option {

        public CenterDisplayOnSelection() {
            super("Center Display on Selection");
        }

        @Override
        protected boolean isChecked() {
            return RnartistConfig.getCenterDisplayOnSelection();
        }

        @Override
        protected void check(boolean check) {
            RnartistConfig.setCenterDisplayOnSelection(check);
        }
    }

    private class FitDisplayOnSelection extends Option {

        public FitDisplayOnSelection() {
            super("Fit Display on Selection");
        }

        @Override
        protected boolean isChecked() {
            return RnartistConfig.getFitDisplayOnSelection();
        }

        @Override
        protected void check(boolean check) {
            RnartistConfig.setFitDisplayOnSelection(check);
        }
    }

}
