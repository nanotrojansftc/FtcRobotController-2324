//package org.firstinspires.ftc.teamcode;
package teamcode;

import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.hardware.dfrobot.HuskyLens;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.internal.system.Deadline;

import java.util.concurrent.TimeUnit;
import com.qualcomm.robotcore.hardware.CRServo;

import teamcode.DriveControl_NanoTorjan;

@TeleOp(name = "TeleOpMain5_mt", group = "TeleOp")


public class TeleOpMain5_mt extends LinearOpMode {

    private DcMotor intake = null;
    private DcMotor lsRight = null;
    private DcMotor lsLeft = null;

    private DcMotor frontLeft = null;
    private DcMotor frontRight = null;
    private DcMotor backLeft = null;
    private DcMotor backRight = null;

    //servo motors
    private CRServo planeLaunch = null;

    //2 claws servo motors
    private Servo clawLeft = null;
    private Servo clawRight = null;

    //2 arms servo motors
    private Servo clawLift = null;
    private Servo armLift = null;
    private CRServo robotLift = null;




    private final double driveAdjuster = 1;

    // the following are for huskylen
    private HuskyLens huskyLens;
    private final int READ_PERIOD = 1;

    BNO055IMU imu;
    Orientation angles;

    private DriveControl_NanoTorjan driveControl;
    //private DriveControl driveControl;

    @Override
    public void runOpMode()  throws InterruptedException {
        frontLeft = hardwareMap.dcMotor.get("frontLeft");
        backLeft = hardwareMap.dcMotor.get("backLeft");
        frontRight = hardwareMap.dcMotor.get("frontRight");
        backRight = hardwareMap.dcMotor.get("backRight");

        lsRight = hardwareMap.dcMotor.get("lsRight");
        lsLeft = hardwareMap.dcMotor.get("lsLeft");
        intake = hardwareMap.dcMotor.get("intake");

        //Servo Motors
        planeLaunch = hardwareMap.crservo.get("planeLaunch");
        robotLift = hardwareMap.crservo.get("robotLift");

        //hang


        // get 2 claw motors
        clawLeft = hardwareMap.servo.get("clawLeft");
        clawRight = hardwareMap.servo.get("clawRight");

        // get 2 arm motors
        clawLift = hardwareMap.servo.get("clawLift");
        armLift = hardwareMap.servo.get("armLift");


        // huskyLens = hardwareMap.get(HuskyLens.class, "huskylens");

        BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();
        parameters.loggingEnabled = true;
        parameters.loggingTag = "IMU";
        imu = hardwareMap.get(BNO055IMU.class, "imu");
        imu.initialize(parameters);

        Deadline rateLimit = new Deadline(READ_PERIOD, TimeUnit.SECONDS);
        rateLimit.expire();

        /* if (!huskyLens.knock()) {
            telemetry.addData(">>", "Problem communicating with " + huskyLens.getDeviceName());
        } else {
            telemetry.addData(">>", "Press start to continue");
        } */


        //huskyLens.selectAlgorithm(HuskyLens.Algorithm.TAG_RECOGNITION);
        // huskyLens.selectAlgorithm(HuskyLens.Algorithm.OBJECT_TRACKING);
        //********************** Husky Lens end *********************


        telemetry.update();
        // because the gear is always outside or inside then one size of wheels need to be revers
        frontRight.setDirection(DcMotor.Direction.REVERSE);
        backRight.setDirection(DcMotor.Direction.REVERSE);

        driveControl = new DriveControl_NanoTorjan(frontLeft, frontRight, backLeft, backRight, imu);
        //driveControl = new DriveControl(frontLeft, frontRight, backLeft, backRight, imu);


        Thread baseControlThread = new Thread(new baseControl());
        Thread armControlThread = new Thread(new armControl());

        //Start 2  threads
        baseControlThread.start();
        armControlThread.start();

        // This is the 3rd thread
        //The following  loop is just to keep this main thread running.
        // Add above 2 threads basicall we have 3 threads running
        while (opModeIsActive()) {
            //put some code here for more actions on the control thread
            // Game Pad 2 controller for other motors
            // control intake motor
            intake.setPower(gamepad2.left_stick_y * 0.5);

        }
    }
    private void closeClaw()
    {
        //for the claw, it is a regular motor so you set positions; you just have to keep tweaking the code and test out positions that you input.
        clawLeft.setPosition(1);
        clawRight.setPosition(0);
    }
    private void openClaw()
    {
        //for the claw, it is a regular motor so you set positions; you just have to keep tweaking the code and test out positions that you input.
        clawLeft.setPosition(0.15);
        clawRight.setPosition(0.8);
    }
    private void hangSpin()
    {
        //since this servo is continuous, we have to use set power like motors; then sleep 1000 milliseconds which is equal to one second before turning the servo off.
        //you can also hold down the button to continuously turn the servo instead of pressing multiple times.
        robotLift.setPower(1);
        sleep(1000);
        robotLift.setPower(0);
    }
    private void reversehangSpin()
    {
        // this is the same as the hang spin except reversing the servo so it goes the other way so that we can unload the tension.
        robotLift.setPower(-1);
        sleep(1000);
        robotLift.setPower(0);
    }
    private void planeLaunch()
    {
        //plane launch also uses a continuous servo so it has the same concept as the hang mechanism, it basically just turns the servo for a second and then turns it off.
        planeLaunch.setPower(1);
        sleep(1000);
        planeLaunch.setPower(0);
    }
    private void clawDown()
    {
        //these follow the same concept as the claw, except it only needs to move one servo.
        clawLift.setPosition(0.525);
    }
    private void clawUp()
    {
        //same thing for this except the position is different.
        clawLift.setPosition(0.8);
    }
    private void clawFull()
    {
        clawLift.setPosition(1);
    }
    private void armUp()
    {
        //same concept as clawUp, just on the arm.
        armLift.setPosition(0.5);
    }
    private void armDown()
    {
        //same thing as armUp but with a different position.
        armLift.setPosition(0.125);
    }
    private void armFull()
    {
        armLift.setPosition(0.8);
    }
    private void smallls()
    {
        //since this is a motor, it uses power, this works the same as robotlift but one of the powers are negative because one of the motors is facing the opposite way.
        //since this is the small linear slide lift, we only wait 250 milliseconds which is equivilant to a quarter of a second; then we just turn off the motors.
        lsRight.setPower(-1);
        lsLeft.setPower(1);
        sleep(250);
        lsRight.setPower(0);
        lsLeft.setPower(0);
    }
    private void reversesmallls()
    {
        lsRight.setPower(1);
        lsLeft.setPower(-1);
        sleep(250);
        lsRight.setPower(0);
        lsLeft.setPower(0);
    }
    private void mediumls()
    {
        //same as smallls but let the motor run for longer.
        lsRight.setPower(-1);
        lsLeft.setPower(1);
        sleep(1250);
        lsRight.setPower(0);
        lsLeft.setPower(0);
    }
    private void reversemediumls()
    {
        lsRight.setPower(1);
        lsLeft.setPower(-1);
        sleep(1250);
        lsRight.setPower(0);
        lsLeft.setPower(0);
    }

    private void highls()
    {
        //same as mediumls but let motor run for even longer.
        //this is only raising it from medium by a little bit because we wont go any higher than that within the time limit.
        lsRight.setPower(-1);
        lsLeft.setPower(1);
        sleep(2000);
        lsRight.setPower(0);
        lsLeft.setPower(0);
    }
    private void reversehighls()
    {
        lsRight.setPower(1);
        lsLeft.setPower(-1);
        sleep(2000);
        lsRight.setPower(0);
        lsLeft.setPower(0);
    }




    // This is the class to control the base of the robot to move arround, this normally is
    // controlled by one person
    private class baseControl implements Runnable {
        @Override
        public void run() {
            while (!Thread.interrupted() && opModeIsActive()) {
                // Motor control logic for motors 1 and 2
                //Call Robot base movement algorithem to drive the base
                driveControl.driveRobot(gamepad1.left_stick_x, gamepad1.left_stick_y, gamepad1.right_stick_x);

                if(gamepad1.left_bumper){
                    hangSpin();
                    sleep(1000);



                }
                if(gamepad1.right_bumper){
                    reversehangSpin();


                }


            }
        }
    }//end of class baseControl

    // This is the class to control arms , claws
    private class armControl implements Runnable {
        @Override
        public void run() {

            //intake.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            double lspower = 0;
            boolean moveup = false;
            boolean moveup2 = false;
            boolean moveup3 = false;
            boolean lsmove = false;
            boolean lsmove2 = false;
            boolean clawopen = false;
            boolean clawup = false;
            boolean defaultscore = false;
            boolean mediumscore = false;
            boolean highscore = false;
            boolean hang = false;
            boolean hangcount = false;


            //waitForStart();
            //set closed claw and claw lift down
            clawLeft.setPosition(0.25);
            clawRight.setPosition(0.8);
            clawLift.setPosition(0.525);
            //while (!isStopRequested()) {
            while (!Thread.interrupted() && opModeIsActive()) {

                //lift power take from the second game pad
                lspower = gamepad2.right_stick_y;
                lsRight.setPower(lspower);
                lsLeft.setPower(-lspower);


                /* HuskyLens.Block[] blocks = huskyLens.blocks();
                   telemetry.addData("Block count", blocks.length);
                   for (int i = 0; i < blocks.length; i++) {
                       telemetry.addData("Block", blocks[i].toString());
                } */

                //Plane launcher
                //if(gamepad2.left_trigger >=0.1){
//                if (gamepad2.y) {
//                    // 1 is the after launch position
//                    planeLaunch.setPower(1);
//                    // after launched wait 1.5 seconds move back to ready position
//                    sleep(1000);
//                    // 0.4 is the ready position
//                    //planeLaunch.setPosition(0.4);
//                    //sleep(1000);
//                    planeLaunch.setPower(0);
//                }

                //Claw contols  -  close and open, when the claw is closed, then open it, when claw is open, then close it
                if (gamepad2.left_bumper) {
                    planeLaunch();
                }
                if (gamepad2.right_bumper) {
                    //if claw is closed then open it
                    if (clawopen == false) {
                        openClaw();
                        sleep(250);

                    }
                    //if claw is opened then close it
                    else {
                        closeClaw();
                        sleep(250);
                    }
                    clawopen = !clawopen;
                }

                //Claw - move up and down, when its already up, move it down, when its already down, then move up
                if (gamepad2.right_trigger >= 0.1) {
                    if (clawup) {
                        clawDown();
                        sleep(250);
                    } else {
                        clawFull();
                        sleep(250);
                    }
                    clawup = !clawup;
                }
                //make the arm lift so we can manually reset it
                if (gamepad2.a) {
                    armUp();
                }
                //make the arm go back down to default position on the ground
                if (gamepad2.x) {
                    armDown();
                }
                if (gamepad2.dpad_up) {
                    if (defaultscore == false) {
                        //move up linear slides
                        smallls();
                        //end move up
                        armFull();
                        sleep(500);
                        clawUp();
                        moveup = true;
                        sleep(250);
                    }
                    //automation to reset position
                    else if (defaultscore == true) {
                        if (moveup) {
                            //reset linear slides only if it was up
                            reversesmallls();
                            moveup = false;
                        }
                        armUp();
                        sleep(1000);
                        clawUp();
                        clawFull();
                        closeClaw();
                        sleep(250);
                        clawDown();
                        openClaw();
                        clawup = false;
                        clawopen = true;
                        sleep(250);
                    }
                    defaultscore = !defaultscore;
                }
                if (gamepad2.dpad_right) {
                    if (mediumscore == false) {
                        smallls();
                        //end move up
                        armFull();
                        sleep(1500);
                        clawFull();
                        mediumls();
                        moveup3 = true;
                        lsmove2 = true;
                        sleep(250);
                    } else if (mediumscore == true) {
                        armUp();
                        sleep(1500);
                        clawUp();
                        if (moveup3) {
                            //reset linear slides only if it was up
                            reversesmallls();
                            moveup3 = false;
                        }
                        if (lsmove2) {
                            reversemediumls();
                            lsmove2 = false;

                        }
                        closeClaw();
                        armDown();
                        sleep(250);
                        clawDown();
                        openClaw();
                        clawup = false;
                        clawopen = true;
                        sleep(250);
                    }
                    mediumscore = !mediumscore;
                }
                //automation to score pixel
                if (gamepad2.dpad_down) {
                    if (highscore == false) {
                        smallls();
                        //end move up
                        armFull();
                        sleep(1500);
                        clawFull();
                        //linear slide go up
                        highls();
                        moveup2 = true;
                        lsmove = true;
                        sleep(250);
                    } else if (highscore == true) {
                        armUp();
                        sleep(500);
                        clawUp();
                        if (moveup2) {
                            //reset linear slides only if it was up
                            reversesmallls();
                            moveup2 = false;
                        }
                        if (lsmove) {
                            reversehighls();
                            lsmove = false;
                        }
                        closeClaw();
                        armDown();
                        sleep(250);
                        clawDown();
                        openClaw();
                        clawup = false;
                        clawopen = true;
                        sleep(250);
                    }
                    highscore = !highscore;
                }
                telemetry.update();
            }
        } //end of class armControl.run()
    }//end of class armControl

}//end of main class to3controlchange_ms
