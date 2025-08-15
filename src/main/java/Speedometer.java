import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

public class Speedometer extends Region {

    private final Canvas canvas;
    private final DoubleProperty value = new SimpleDoubleProperty(0);
    private final Timeline timeline = new Timeline();

    private static final double[] SCALE_POINTS = {0, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
    private static final double[] SCALE_ANGLES = {180, 200, 215, 235, 255, 275, 295, 315, 330, 345, 360};

    public Speedometer() {
        canvas = new Canvas();
        getChildren().add(canvas);

        value.addListener((obs, oldVal, newVal) -> {
            timeline.stop();
            KeyValue kv = new KeyValue(value, newVal.doubleValue());
            KeyFrame kf = new KeyFrame(Duration.millis(250), kv);
            timeline.getKeyFrames().setAll(kf);
            timeline.play();
        });
        
        timeline.currentTimeProperty().addListener(e -> draw());
    }
    
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        final double width = getWidth();
        final double height = getHeight();
        if (width > 0 && height > 0) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            draw();
        }
    }

    public void setValue(double value) { this.value.set(value); }

    private double valueToAngle(double speed) {
        if (speed <= SCALE_POINTS[0]) return SCALE_ANGLES[0];
        if (speed >= SCALE_POINTS[SCALE_POINTS.length - 1]) return SCALE_ANGLES[SCALE_POINTS.length - 1];

        for (int i = 0; i < SCALE_POINTS.length - 1; i++) {
            if (speed >= SCALE_POINTS[i] && speed <= SCALE_POINTS[i + 1]) {
                double speedRange = SCALE_POINTS[i + 1] - SCALE_POINTS[i];
                double angleRange = SCALE_ANGLES[i + 1] - SCALE_ANGLES[i];
                double speedProgress = (speed - SCALE_POINTS[i]) / speedRange;
                return SCALE_ANGLES[i] + (speedProgress * angleRange);
            }
        }
        return SCALE_ANGLES[SCALE_ANGLES.length - 1];
    }

    private void draw() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        
        double centerX = width / 2;
        double radius = Math.min(width * 0.45, height * 0.8);
        double centerY = radius + 20;

        gc.clearRect(0, 0, width, height);

        for (int i = 0; i < SCALE_POINTS.length; i++) {
            double angle = SCALE_ANGLES[i];
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(radius * 0.02);
            double x1 = centerX + radius * Math.cos(Math.toRadians(angle));
            double y1 = centerY + radius * Math.sin(Math.toRadians(angle));
            double x2 = centerX + (radius * 0.9) * Math.cos(Math.toRadians(angle));
            double y2 = centerY + (radius * 0.9) * Math.sin(Math.toRadians(angle));
            gc.strokeLine(x1, y1, x2, y2);

            gc.setFill(Color.web("#5a5c69"));
            gc.setFont(Font.font("System", FontWeight.NORMAL, radius * 0.08));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            double textX = centerX + (radius * 0.8) * Math.cos(Math.toRadians(angle));
            double textY = centerY + (radius * 0.8) * Math.sin(Math.toRadians(angle));
            gc.fillText(String.format("%.0f", SCALE_POINTS[i]), textX, textY);
            
            if (i < SCALE_POINTS.length - 1) {
                gc.setStroke(Color.LIGHTGRAY);
                gc.setLineWidth(radius * 0.01);
                int minorTicks = 4;
                double angleStep = (SCALE_ANGLES[i+1] - SCALE_ANGLES[i]) / (minorTicks + 1);
                for(int j=1; j <= minorTicks; j++) {
                    double minorAngle = angle + j * angleStep;
                    double mx1 = centerX + radius * Math.cos(Math.toRadians(minorAngle));
                    double my1 = centerY + radius * Math.sin(Math.toRadians(minorAngle));
                    double mx2 = centerX + (radius * 0.95) * Math.cos(Math.toRadians(minorAngle));
                    double my2 = centerY + (radius * 0.95) * Math.sin(Math.toRadians(minorAngle));
                    gc.strokeLine(mx1, my1, mx2, my2);
                }
            }
        }
        
        double currentAngle = valueToAngle(value.get());
        gc.save();
        gc.translate(centerX, centerY);
        gc.rotate(currentAngle);
        gc.beginPath();
        gc.moveTo(0, 0);
        gc.lineTo(0, -(radius * 0.04));
        gc.lineTo(radius * 0.85, 0);
        gc.lineTo(0, radius * 0.04);
        gc.closePath();
        gc.setFill(Color.ROYALBLUE);
        gc.fill();
        gc.restore();

        gc.setFill(Color.ROYALBLUE);
        gc.fillOval(centerX - (radius * 0.08), centerY - (radius * 0.08), radius * 0.16, radius * 0.16);
        gc.setFill(Color.WHITE);
        gc.fillOval(centerX - (radius * 0.04), centerY - (radius * 0.04), radius * 0.08, radius * 0.08);

        gc.setFill(Color.BLACK);
        gc.setFont(Font.font("System", FontWeight.BOLD, radius * 0.4));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.format("%.0f", value.get()), centerX, centerY - (radius * 0.30)); 
        
        gc.setFill(Color.web("#858796"));
        gc.setFont(Font.font("System", radius * 0.12));
        gc.fillText("Mbps", centerX, centerY - (radius * 0.10));
    }
}