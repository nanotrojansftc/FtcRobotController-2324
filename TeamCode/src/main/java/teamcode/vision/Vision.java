/*
 * Copyright (c) 2023 Titan Robotics Club (http://www.titanrobotics.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package teamcode.vision;

import org.firstinspires.ftc.robotcore.external.hardware.camera.BuiltinCameraDirection;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.vision.VisionProcessor;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.firstinspires.ftc.vision.tfod.TfodProcessor;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;

import java.util.ArrayList;

import TrcCommonLib.trclib.TrcDbgTrace;
import TrcCommonLib.trclib.TrcOpenCvColorBlobPipeline;
import TrcCommonLib.trclib.TrcOpenCvDetector;
import TrcCommonLib.trclib.TrcPose2D;
import TrcCommonLib.trclib.TrcVisionTargetInfo;
import TrcFtcLib.ftclib.FtcEocvColorBlobProcessor;
import TrcFtcLib.ftclib.FtcOpMode;
import TrcFtcLib.ftclib.FtcRawEocvColorBlobPipeline;
import TrcFtcLib.ftclib.FtcRawEocvVision;
import TrcFtcLib.ftclib.FtcVision;
import TrcFtcLib.ftclib.FtcVisionAprilTag;
import TrcFtcLib.ftclib.FtcVisionEocvColorBlob;
import TrcFtcLib.ftclib.FtcVisionTensorFlow;
import teamcode.Robot;
import teamcode.RobotParams;
import teamcode.subsystems.BlinkinLEDs;

/**
 * This class implements AprilTag/TensorFlow/Eocv Vision for the game season. It creates and initializes all the
 * vision target info as well as providing info for the robot, camera and the field. It also provides methods to get
 * the location of the robot and detected targets.
 */
public class Vision
{
    private static final String moduleName = "Vision";
    // Warning: EOCV converts camera stream to RGBA whereas Desktop OpenCV converts it to BGRA. Therefore, the correct
    // color conversion must be RGBA (or RGB) to whatever color space you want to convert.
    //
    // YCrCb Color Space.
    private static final int colorConversion = Imgproc.COLOR_RGB2YCrCb;
    private static final double[] redBlobColorThresholds = {20.0, 120.0, 180.0, 240.0, 90.0, 120.0};
    private static final double[] blueBlobColorThresholds = {40.0, 140.0, 100.0, 150.0, 150.0, 200.0};
    private static final TrcOpenCvColorBlobPipeline.FilterContourParams colorBlobFilterContourParams =
        new TrcOpenCvColorBlobPipeline.FilterContourParams()
            .setMinArea(5000.0)
            .setMinPerimeter(200.0)
            .setWidthRange(50.0, 1000.0)
            .setHeightRange(80.0, 1000.0)
            .setSolidityRange(0.0, 100.0)
            .setVerticesRange(0.0, 1000.0)
            .setAspectRatioRange(0.3, 1.0);

    private static final String TFOD_MODEL_ASSET = "MyObject.tflite";
    private static final float TFOD_MIN_CONFIDENCE = 0.75f;
    public static final String TFOD_OBJECT_LABEL = "MyObject";
    public static final String[] TFOD_TARGET_LABELS = {TFOD_OBJECT_LABEL};

    private final Robot robot;
    private final WebcamName webcam1, webcam2;
    private FtcRawEocvColorBlobPipeline rawColorBlobPipeline;
    public FtcRawEocvVision rawColorBlobVision;
    public FtcVisionAprilTag aprilTagVision;
    private AprilTagProcessor aprilTagProcessor;
    public FtcVisionEocvColorBlob redBlobVision;
    private FtcEocvColorBlobProcessor redBlobProcessor;
    public FtcVisionEocvColorBlob blueBlobVision;
    private FtcEocvColorBlobProcessor blueBlobProcessor;
    public FtcVisionTensorFlow tensorFlowVision;
    private TfodProcessor tensorFlowProcessor;
    public FtcVision vision;

    /**
     * Constructor: Create an instance of the object.
     *
     * @param robot specifies the robot object.
     * @param tracer specifies the tracer for trace info, null if none provided.
     */
    public Vision(Robot robot, TrcDbgTrace tracer)
    {
        FtcOpMode opMode = FtcOpMode.getInstance();

        this.robot = robot;
        this.webcam1 = opMode.hardwareMap.get(WebcamName.class, RobotParams.HWNAME_WEBCAM1);
        this.webcam2 = RobotParams.Preferences.hasWebCam2?
            opMode.hardwareMap.get(WebcamName.class, RobotParams.HWNAME_WEBCAM2): null;
        if (RobotParams.Preferences.tuneColorBlobVision)
        {
            OpenCvCamera openCvCam;

            if (RobotParams.Preferences.showVisionView)
            {
                int cameraViewId = opMode.hardwareMap.appContext.getResources().getIdentifier(
                    "cameraMonitorViewId", "id", opMode.hardwareMap.appContext.getPackageName());
                openCvCam = OpenCvCameraFactory.getInstance().createWebcam(webcam1, cameraViewId);
                openCvCam.showFpsMeterOnViewport(false);
            }
            else
            {
                openCvCam = OpenCvCameraFactory.getInstance().createWebcam(webcam1);
            }

            robot.globalTracer.traceInfo(moduleName, "Starting RawEocvColorBlobVision...");
            rawColorBlobPipeline = new FtcRawEocvColorBlobPipeline(
                "rawColorBlobPipeline", colorConversion, redBlobColorThresholds, colorBlobFilterContourParams, true,
                tracer);
            // By default, display original Mat.
            rawColorBlobPipeline.setVideoOutput(0);
            rawColorBlobPipeline.setAnnotateEnabled(true);
            rawColorBlobVision = new FtcRawEocvVision(
                "rawColorBlobVision", RobotParams.CAM_IMAGE_WIDTH, RobotParams.CAM_IMAGE_HEIGHT, null, null,
                openCvCam, RobotParams.CAM_ORIENTATION, tracer);
            setRawColorBlobVisionEnabled(false);
        }
        else
        {
            // Creating Vision Processors for VisionPortal.
            ArrayList<VisionProcessor> visionProcessorsList = new ArrayList<>();

            if (RobotParams.Preferences.useAprilTagVision)
            {
                robot.globalTracer.traceInfo(moduleName, "Starting AprilTagVision...");
                FtcVisionAprilTag.Parameters aprilTagParams = new FtcVisionAprilTag.Parameters()
                    .setDrawTagIdEnabled(true)
                    .setDrawTagOutlineEnabled(true)
                    .setDrawAxesEnabled(false)
                    .setDrawCubeProjectionEnabled(false)
//                    .setLensIntrinsics(
//                        RobotParams.WEBCAM_FX, RobotParams.WEBCAM_FY, RobotParams.WEBCAM_CX, RobotParams.WEBCAM_CY)
                    .setOutputUnits(DistanceUnit.INCH, AngleUnit.DEGREES);
                aprilTagVision = new FtcVisionAprilTag(aprilTagParams, AprilTagProcessor.TagFamily.TAG_36h11, tracer);
                aprilTagProcessor = aprilTagVision.getVisionProcessor();
                visionProcessorsList.add(aprilTagProcessor);
            }

            if (RobotParams.Preferences.useColorBlobVision)
            {
                robot.globalTracer.traceInfo(moduleName, "Starting ColorBlobVision...");

                redBlobVision = new FtcVisionEocvColorBlob(
                    "RedBlob", colorConversion, redBlobColorThresholds, colorBlobFilterContourParams, true,
                    RobotParams.cameraRect, RobotParams.worldRect, true, tracer);
                redBlobProcessor = redBlobVision.getVisionProcessor();
                visionProcessorsList.add(redBlobProcessor);

                blueBlobVision = new FtcVisionEocvColorBlob(
                    "BlueBlob", colorConversion, blueBlobColorThresholds, colorBlobFilterContourParams, true,
                    RobotParams.cameraRect, RobotParams.worldRect, true, tracer);
                blueBlobProcessor = blueBlobVision.getVisionProcessor();
                visionProcessorsList.add(blueBlobProcessor);
            }

            if (RobotParams.Preferences.useTensorFlowVision)
            {
                robot.globalTracer.traceInfo(moduleName, "Starting TensorFlowVision...");
                tensorFlowVision = new FtcVisionTensorFlow(
                    null, true, TFOD_MODEL_ASSET, TFOD_TARGET_LABELS, RobotParams.cameraRect, RobotParams.worldRect,
                    tracer);
                tensorFlowProcessor = tensorFlowVision.getVisionProcessor();
                tensorFlowProcessor.setMinResultConfidence(TFOD_MIN_CONFIDENCE);
                visionProcessorsList.add(tensorFlowProcessor);
            }

            VisionProcessor[] visionProcessors = new VisionProcessor[visionProcessorsList.size()];
            visionProcessorsList.toArray(visionProcessors);
            if (RobotParams.Preferences.useWebCam)
            {
                // Use USB webcams.
                vision = new FtcVision(
                    webcam1, webcam2, RobotParams.CAM_IMAGE_WIDTH, RobotParams.CAM_IMAGE_HEIGHT,
                    RobotParams.Preferences.showVisionView, visionProcessors);
            }
            else
            {
                // Use phone camera.
                vision = new FtcVision(
                    RobotParams.Preferences.useBuiltinCamBack?
                        BuiltinCameraDirection.BACK: BuiltinCameraDirection.FRONT,
                    RobotParams.CAM_IMAGE_WIDTH, RobotParams.CAM_IMAGE_HEIGHT,
                    RobotParams.Preferences.showVisionView, visionProcessors);
            }
            // Disable all vision until they are needed.
            setRawColorBlobVisionEnabled(false);
            for (VisionProcessor processor: visionProcessors)
            {
                vision.setProcessorEnabled(processor, false);
            }
        }
    }   //Vision

    /**
     * This method returns the active camera if we have two webcams.
     *
     * @return active camera.
     */
    public WebcamName getActiveCamera()
    {
        return vision.getActiveCamera();
    }   //getActiveCamera

    /**
     * This method switch the active camera to the other camera if we have two webcams.
     */
    public void switchCamera()
    {
        WebcamName activeCamera = getActiveCamera();

        if (activeCamera == webcam1 && webcam2 != null)
        {
            vision.setActiveCamera(webcam2);
        }
        else if (activeCamera == webcam2 && webcam1 != null)
        {
            vision.setActiveCamera(webcam1);
        }
    }   //switchCamera

    /**
     * This method displays the exposure settings on the dashboard. This helps tuning camera exposure.
     *
     * @param lineNum specifies the dashboard line number to display the info.
     */
    public void displayExposureSettings(int lineNum)
    {
        long[] exposureSetting = vision.getExposureSetting();
        long currExposure = vision.getCurrentExposure();
        int[] gainSetting = vision.getGainSetting();
        int currGain = vision.getCurrentGain();

        robot.dashboard.displayPrintf(
            lineNum, "Exp: %d (%d:%d), Gain: %d (%d:%d)",
            currExposure, exposureSetting[0], exposureSetting[1],
            currGain, gainSetting != null? gainSetting[0]: 0, gainSetting != null? gainSetting[1]: 0);
    }   //displayExposureSettings

    /**
     * This method returns the color threshold values of rawColorBlobVision.
     *
     * @return array of color threshold values.
     */
    public double[] getRawColorBlobThresholds()
    {
        return rawColorBlobPipeline != null? rawColorBlobPipeline.getColorThresholds(): null;
    }   //getRawColorBlobThresholds

    /**
     * This method sets the color threshold values of rawColorBlobVision.
     *
     * @param colorThresholds specifies an array of color threshold values.
     */
    public void setRawColorBlobThresholds(double... colorThresholds)
    {
        if (rawColorBlobPipeline != null)
        {
            rawColorBlobPipeline.setColorThresholds(colorThresholds);
        }
    }   //setRawColorBlobThresholds


    /**
     * This method enables/disables raw ColorBlob vision.
     *
     * @param enabled specifies true to enable, false to disable.
     */
    public void setRawColorBlobVisionEnabled(boolean enabled)
    {
        if (rawColorBlobVision != null)
        {
            rawColorBlobVision.setPipeline(enabled? rawColorBlobPipeline: null);
        }
    }   //setRawColorBlobVisionEnabled

    /**
     * This method checks if raw ColorBlob vision is enabled.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isRawColorBlobVisionEnabled()
    {
        return rawColorBlobVision != null && rawColorBlobVision.getPipeline() != null;
    }   //isRawColorBlobVisionEnabled

    /**
     * This method calls RawColorBlob vision to detect the color blob for color threshold tuning.
     *
     * @param lineNum specifies the dashboard line number to display the detected object info, -1 to disable printing.
     * @return detected raw color blob object info.
     */
    public TrcVisionTargetInfo<TrcOpenCvDetector.DetectedObject<?>> getDetectedRawColorBlob(int lineNum)
    {
        TrcVisionTargetInfo<TrcOpenCvDetector.DetectedObject<?>> colorBlobInfo =
            rawColorBlobVision != null? rawColorBlobVision.getBestDetectedTargetInfo(null, null, 0.0, 0.0): null;

        if (lineNum != -1)
        {
            robot.dashboard.displayPrintf(lineNum, "ColorBlob: %s", colorBlobInfo != null? colorBlobInfo: "Not found.");
        }

        return colorBlobInfo;
    }   //getDetectedRawColorBlob

    /**
     * This method enables/disables AprilTag vision.
     *
     * @param enabled specifies true to enable, false to disable.
     */
    public void setAprilTagVisionEnabled(boolean enabled)
    {
        if (aprilTagProcessor != null)
        {
            vision.setProcessorEnabled(aprilTagProcessor, enabled);
        }
    }   //setAprilTagVisionEnabled

    /**
     * This method checks if AprilTag vision is enabled.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isAprilTagVisionEnabled()
    {
        return aprilTagProcessor != null && vision.isVisionProcessorEnabled(aprilTagProcessor);
    }   //isAprilTagVisionEnabled

    /**
     * This method calls AprilTag vision to detect the AprilTag object.
     *
     * @param id specifies the AprilTag ID to look for, null if match to any ID.
     * @param lineNum specifies the dashboard line number to display the detected object info, -1 to disable printing.
     * @return detected AprilTag object info.
     */
    public TrcVisionTargetInfo<FtcVisionAprilTag.DetectedObject> getDetectedAprilTag(Integer id, int lineNum)
    {
        TrcVisionTargetInfo<FtcVisionAprilTag.DetectedObject> aprilTagInfo =
            robot.vision.aprilTagVision.getBestDetectedTargetInfo(id, null);

        if (aprilTagInfo != null && robot.blinkin != null)
        {
            robot.blinkin.setDetectedPattern(BlinkinLEDs.APRIL_TAG);
        }

        if (lineNum != -1)
        {
            robot.dashboard.displayPrintf(lineNum, "AprilTag: %s", aprilTagInfo != null? aprilTagInfo: "Not found.");
        }

        return aprilTagInfo;
    }   //getDetectedAprilTag

    /**
     * This method calculates the robot's absolute field location with the detected AprilTagInfo.
     *
     * @param aprilTagInfo specifies the detected AprilTag info.
     * @return robot field location.
     */
    public TrcPose2D getRobotFieldPose(TrcVisionTargetInfo<FtcVisionAprilTag.DetectedObject> aprilTagInfo)
    {
        final String funcName = "getRobotFieldPose";
        TrcPose2D robotPose = null;

        if (aprilTagInfo != null)
        {
            TrcPose2D aprilTagPose = RobotParams.APRILTAG_POSES[aprilTagInfo.detectedObj.aprilTagDetection.id - 1];
            robotPose = aprilTagPose.subtractRelativePose(aprilTagInfo.objPose.toPose2D());
            // TODO: adjust robot pose with respect to the camera location on the robot.
            robot.globalTracer.traceInfo(
                funcName, "AprilTagId=%d, aprilTagFieldPose=%s, aprilTagPoseFromCamera=%s, robotPose=%s",
                aprilTagInfo.detectedObj.aprilTagDetection.id, aprilTagPose, aprilTagInfo.objPose.toPose2D(),
                robotPose);
        }

        return robotPose;
    }   //getRobotFieldPose

    /**
     * This method uses vision to find an AprilTag and uses the AprilTag's absolute field location and its relative
     * position from the camera to calculate the robot's absolute field location.
     *
     * @return robot field location.
     */
    public TrcPose2D getRobotFieldPose()
    {
        TrcPose2D robotPose = null;

        if (aprilTagVision != null)
        {
            // Find any AprilTag in view.
            TrcVisionTargetInfo<FtcVisionAprilTag.DetectedObject> aprilTagInfo = getDetectedAprilTag(null, -1);

            if (aprilTagInfo != null)
            {
                robotPose = getRobotFieldPose(aprilTagInfo);
            }
        }

        return robotPose;
    }   //getRobotFieldPose

    /**
     * This method enables/disables RedBlob vision.
     *
     * @param enabled specifies true to enable, false to disable.
     */
    public void setRedBlobVisionEnabled(boolean enabled)
    {
        if (redBlobProcessor != null)
        {
            vision.setProcessorEnabled(redBlobProcessor, enabled);
        }
    }   //setRedBlobVisionEnabled

    /**
     * This method checks if RedBlob vision is enabled.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isRedBlobVisionEnabled()
    {
        return redBlobProcessor != null && vision.isVisionProcessorEnabled(redBlobProcessor);
    }   //isRedBlobVisionEnabled

    /**
     * This method calls ColorBlob vision to detect the Red Blob object.
     *
     * @param lineNum specifies the dashboard line number to display the detected object info, -1 to disable printing.
     * @return detected Red Blob object info.
     */
    public TrcVisionTargetInfo<TrcOpenCvColorBlobPipeline.DetectedObject> getDetectedRedBlob(int lineNum)
    {
        TrcVisionTargetInfo<TrcOpenCvColorBlobPipeline.DetectedObject> colorBlobInfo =
            robot.vision.redBlobVision.getBestDetectedTargetInfo(null, null, 0.0, 0.0);

        if (colorBlobInfo != null && robot.blinkin != null)
        {
            robot.blinkin.setDetectedPattern(BlinkinLEDs.RED_BLOB);
        }

        if (lineNum != -1)
        {
            robot.dashboard.displayPrintf(
                lineNum, "RedBlob: %s", colorBlobInfo != null? colorBlobInfo: "Not found.");
        }

        return colorBlobInfo;
    }   //getDetectedRedBlob

    /**
     * This method enables/disables BlueBlob vision.
     *
     * @param enabled specifies true to enable, false to disable.
     */
    public void setBlueBlobVisionEnabled(boolean enabled)
    {
        if (blueBlobProcessor != null)
        {
            vision.setProcessorEnabled(blueBlobProcessor, enabled);
        }
    }   //setBlueBlobVisionEnabled

    /**
     * This method checks if BlueBlob vision is enabled.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isBlueBlobVisionEnabled()
    {
        return blueBlobProcessor != null && vision.isVisionProcessorEnabled(blueBlobProcessor);
    }   //isBlueBlobVisionEnabled

    /**
     * This method calls ColorBlob vision to detect the Blue Blob object.
     *
     * @param lineNum specifies the dashboard line number to display the detected object info, -1 to disable printing.
     * @return detected Blue Blob object info.
     */
    public TrcVisionTargetInfo<TrcOpenCvColorBlobPipeline.DetectedObject> getDetectedBlueBlob(int lineNum)
    {
        TrcVisionTargetInfo<TrcOpenCvColorBlobPipeline.DetectedObject> colorBlobInfo =
            robot.vision.blueBlobVision.getBestDetectedTargetInfo(null, null, 0.0, 0.0);

        if (colorBlobInfo != null && robot.blinkin != null)
        {
            robot.blinkin.setDetectedPattern(BlinkinLEDs.BLUE_BLOB);
        }

        if (lineNum != -1)
        {
            robot.dashboard.displayPrintf(
                lineNum, "GreenPixel: %s", colorBlobInfo != null? colorBlobInfo: "Not found.");
        }

        return colorBlobInfo;
    }   //getDetectedBlueBlob

    /**
     * This method enables/disables TensorFlow vision.
     *
     * @param enabled specifies true to enable, false to disable.
     */
    public void setTensorFlowVisionEnabled(boolean enabled)
    {
        if (tensorFlowProcessor != null)
        {
            vision.setProcessorEnabled(tensorFlowProcessor, enabled);
        }
    }   //setTensorFlowVisionEnabled

    /**
     * This method checks if TensorFlow vision is enabled.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isTensorFlowVisionEnabled()
    {
        return tensorFlowProcessor != null && vision.isVisionProcessorEnabled(tensorFlowProcessor);
    }   //isTensorFlowVisionEnabled

    /**
     * This method calls TensorFlow vision to detect the Pixel objects.
     *
     * @param lineNum specifies the dashboard line number to display the detected object info, -1 to disable printing.
     * @return detected Pixel object info.
     */
    public TrcVisionTargetInfo<FtcVisionTensorFlow.DetectedObject> getDetectedTensorFlowPixel(int lineNum)
    {
        TrcVisionTargetInfo<FtcVisionTensorFlow.DetectedObject> tensorFlowInfo =
            robot.vision.tensorFlowVision.getBestDetectedTargetInfo(
                TFOD_OBJECT_LABEL, null, this::compareConfidence, 0.0, 0.0);

        if (tensorFlowInfo != null && robot.blinkin != null)
        {
            robot.blinkin.setDetectedPattern(BlinkinLEDs.TENSOR_FLOW);
        }

        if (lineNum != -1)
        {
            robot.dashboard.displayPrintf(
                lineNum, "TensorFlow: %s", tensorFlowInfo != null? tensorFlowInfo: "Not found.");
        }

        return tensorFlowInfo;
    }   //getDetectedTensorFlowPixel

    /**
     * This method is called by the Arrays.sort to sort the target object by increasing distance.
     *
     * @param a specifies the first target
     * @param b specifies the second target.
     * @return negative value if a has closer distance than b, 0 if a and b have equal distances, positive value
     *         if a has higher distance than b.
     */
    private int compareDistance(
        TrcVisionTargetInfo<TrcOpenCvColorBlobPipeline.DetectedObject> a,
        TrcVisionTargetInfo<TrcOpenCvColorBlobPipeline.DetectedObject> b)
    {
        return (int)((b.objPose.y - a.objPose.y)*100);
    }   //compareDistance

    /**
     * This method is called by the Arrays.sort to sort the target object by decreasing confidence.
     *
     * @param a specifies the first target
     * @param b specifies the second target.
     * @return negative value if a has higher confidence than b, 0 if a and b have equal confidence, positive value
     *         if a has lower confidence than b.
     */
    private int compareConfidence(
        TrcVisionTargetInfo<FtcVisionTensorFlow.DetectedObject> a,
        TrcVisionTargetInfo<FtcVisionTensorFlow.DetectedObject> b)
    {
        return (int)((b.detectedObj.confidence - a.detectedObj.confidence)*100);
    }   //compareConfidence

}   //class Vision
