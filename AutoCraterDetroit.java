package org.firstinspires.ftc.teamcode;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.robotcore.external.tfod.TFObjectDetector;

import java.util.List;

import static com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_TO_POSITION;
import static com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_USING_ENCODER;
import static com.qualcomm.robotcore.hardware.DcMotor.RunMode.RUN_WITHOUT_ENCODER;
import static com.qualcomm.robotcore.hardware.DcMotor.RunMode.STOP_AND_RESET_ENCODER;
import static java.lang.Math.abs;
import static java.lang.Math.round;

@Autonomous(name="AutoCraterDetroit", group="Detroit")

public class AutoCraterDetroit extends LinearOpMode {

    /* Declare OpMode members. */
    Hardware_Connection robot = new Hardware_Connection();


    static final double COUNTS_PER_MOTOR_NEVEREST40 = 1120;    // eg: NEVEREST40 Motor Encoder
    static final double DRIVE_GEAR_REDUCTION_NEVEREST40 = 1;     // This is < 1.0 if geared UP
    static final double WHEEL_DIAMETER_CM = 10.5360;     // For figuring circumference
    static final double PULLEY_DIAMETER_CM = 4;
    static final double STEER = 0.93; //friction coefficiant.
    static final int COUNTS_PER_CM_ANDYMARK_WHEEL = (int) ((COUNTS_PER_MOTOR_NEVEREST40 * DRIVE_GEAR_REDUCTION_NEVEREST40) / (WHEEL_DIAMETER_CM * Pi.getNumber()) * STEER);

    static final double COUNTS_PER_MOTOR_TETRIX = 1440;
    static final int ARM_GEAR_REDUCTION_TETRIX = 9;
    static final long TETRIX_MOTOR_ANGLES = round ((COUNTS_PER_MOTOR_TETRIX * (float) ARM_GEAR_REDUCTION_TETRIX / 360) * 2);//calculate of 2 degrees

    private ElapsedTime runtime = new ElapsedTime();
    private VuforiaLocalizer vuforia;
    private TFObjectDetector tfod;
    List<Recognition> updatedRecognitions;
    double DriveY = 0;
    double DriveX = 0;
    int Degree = 0;
    double DrivePower = 0;
    double TurnPower = 0;

    static final double HEADING_THRESHOLD = 1;      // As tight as we can make it with an integer gyro
    static final double P_TURN_COEFF = 0.05;     // Larger is more responsive, but also less stable
    static final double P_DRIVE_COEFF = 0.05;     // Larger is more responsive, but also less stable
    private static final String TFOD_MODEL_ASSET = "RoverRuckus.tflite";
    private static final String LABEL_GOLD_MINERAL = "Gold Mineral";
    private static final String LABEL_SILVER_MINERAL = "Silver Mineral";
    private static final String VUFORIA_KEY = "AdztnQD/////AAABmTi3BA0jg0pqo1JcP43m+HQ09hcSrJU5FcbzN8MIqJ5lqy9rZzpO8BQT/FB4ezNV6J8XJ6oWRIII5L18wKbeTxlfRahbV3DUl48mamjtSoJgYXX95O0zaUXM/awgtEcKRF15Y/jwmVB5NaoJ3XMVCVmmjkDoysLvFozUttPZKcZ4C9AUcnRBQYYJh/EBSmk+VISyjHZw28+GH2qM3Z2FnlAY6gNBNCHiQvj9OUQSJn/wTOyCeI081oXDBt0BznidaNk0FFq0V0Qh2a/ZiUiSVhsWOdaCudwJlzpKzaoDmxPDujtizvjmPR4JYYkmUX85JZT/EMX4KgoCb2WaYSGK7hkx5oAnY4QC72hSnO83caqF";

    public boolean ErrorOnArmOpen=false;

    public enum gyroDriveDirection {
        LEFTandRIGHT,
        FORWARDandBACKWARD,
        DIAGONALRIGHT,
        DIAGONALLEFT
    }


    public enum motorType {
        ARM,
        DRIVE,
        OPENING_SYSTEM,
        All
    }

    private enum GoldPos {
        Right,
        Left,
        Center,
        None;
    }

    private enum findGoldPosZone {
        Regular,
        Right
    }

    private GoldPos goldPos = GoldPos.None;

    public void runOpMode() {
        robot.init(hardwareMap);
        initVuforia();
        goldPos = GoldPos.None;
        if (ClassFactory.getInstance().canCreateTFObjectDetector()) {
            initTfod();
        } else {
            telemetry.addData("Sorry!", "This device is not compatible with TFOD");
        }
        if (tfod != null)
            tfod.activate();
        //waiting for the user to press start.
        telemetry.addData("angle", gyroGetAngle());
        telemetry.update();
        waitForStart();
        robot.fullEncoderSetMode(RUN_WITHOUT_ENCODER);
        if (opModeIsActive()) {
            climbDown();
            sampling(GoldPos.Right);
            putTeamMarker();
            //goToCrater();
            //pickUpMineral();

        }
    }


    public void gyroDrive(double speed,
                          int distance,
                          double angle,
                          double FreeFlowAngle,
                          gyroDriveDirection direction) {
        int newLeftFrontTarget;
        int newRightFrontTarget;
        int newLeftBackTarget;
        int newRightBackTarget;


        robot.drivingSetMode(RUN_USING_ENCODER);
        double max;
        double error;
        double steer;
        double leftSpeed;
        double rightSpeed;
        double backSpeed;
        double frontSpeed;
        angle += gyroGetAngle();


        if (direction == gyroDriveDirection.FORWARDandBACKWARD) {
            telemetry.addData("gyroDrive", "gyroDrive");
            telemetry.update();

            // Ensure that the opmode is still active
            if (opModeIsActive()) {

                // Determine new target position, and pass to motor controller
                distance = (int) (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
                newLeftFrontTarget = robot.left_front_motor.getCurrentPosition() + distance;
                newRightFrontTarget = robot.right_front_motor.getCurrentPosition() + distance;
                newRightBackTarget = robot.right_back_motor.getCurrentPosition() + distance;
                newLeftBackTarget = robot.left_back_motor.getCurrentPosition() + distance;

                // Set Target and Turn On RUN_TO_POSITION
                robot.left_front_motor.setTargetPosition(newLeftFrontTarget);
                robot.right_front_motor.setTargetPosition(newRightFrontTarget);
                robot.right_back_motor.setTargetPosition(newRightBackTarget);
                robot.left_back_motor.setTargetPosition(newLeftBackTarget);

                robot.drivingSetMode(RUN_TO_POSITION);

                // start motion.
                speed = Range.clip(abs(speed), 0.0, 1.0);
                robot.fullDriving(speed, speed);

                // keep looping while we are still active, and BOTH motors are running.
                while (opModeIsActive()) {
                    if (!robot.left_back_motor.isBusy() || !robot.left_front_motor.isBusy() ||
                            !robot.right_back_motor.isBusy() || !robot.right_front_motor.isBusy()) {
                        robot.fullDriving(0, 0);
                        break;
                    }

                    // adjust relative speed based on heading error.
                    error = getError(angle);
                    steer = getSteer(error, P_DRIVE_COEFF);

                    // if driving in reverse, the motor correction also needs to be reversed
                    if (distance < 0)
                        steer *= -1.0;

                    leftSpeed = speed - steer;
                    rightSpeed = speed + steer;

                    // Normalize speeds if either one exceeds +/- 1.0;
                    max = Math.max(abs(leftSpeed), abs(rightSpeed));
                    if (max > 1.0) {
                        leftSpeed /= max;
                        rightSpeed /= max;
                    }
                    telemetry.addData("Angle",FreeFlowAngle);
                    FreeFlowAngle+=135;

                    DriveY = leftSpeed * Math.sin(Math.toRadians(FreeFlowAngle));
                    DriveX = rightSpeed * Math.cos(Math.toRadians(FreeFlowAngle));
                    robot.diagonalLeft(-DriveY);
                    robot.diagonalRight(-DriveX);

                    // Display drive status for the driver.
                    telemetry.addData("Err/St", "%5.1f/%5.1f", error, steer);
                    telemetry.addData("Target", "%7d:%7d", newLeftFrontTarget, newRightFrontTarget, newLeftBackTarget, newRightBackTarget);
                    telemetry.addData("Actual", "%7d:%7d", robot.left_front_motor.getCurrentPosition(), robot.right_front_motor.getCurrentPosition(), robot.right_back_motor.getCurrentPosition(), robot.left_back_motor.getCurrentPosition());
                    telemetry.addData("Speed", "%5.2f:%5.2f", leftSpeed, rightSpeed);
                    telemetry.update();
                }


                // Stop all motion;
                robot.fullDriving(0, 0);
                robot.drivingSetMode(RUN_USING_ENCODER);
            }

        } else if (direction == gyroDriveDirection.LEFTandRIGHT) {

            if (opModeIsActive()) {

                // Determine new target position, and pass to motor controller
                distance = (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
                newLeftFrontTarget = robot.left_front_motor.getCurrentPosition() - distance;
                newRightFrontTarget = robot.right_front_motor.getCurrentPosition() + distance;
                newRightBackTarget = robot.right_back_motor.getCurrentPosition() - distance;
                newLeftBackTarget = robot.left_back_motor.getCurrentPosition() + distance;

                // Set Target and Turn On RUN_TO_POSITION
                robot.left_front_motor.setTargetPosition(newLeftFrontTarget);
                robot.right_front_motor.setTargetPosition(newRightFrontTarget);
                robot.right_back_motor.setTargetPosition(newRightBackTarget);
                robot.left_back_motor.setTargetPosition(newLeftBackTarget);

                robot.left_back_motor.setMode(RUN_TO_POSITION);
                robot.left_front_motor.setMode(RUN_TO_POSITION);
                robot.right_back_motor.setMode(RUN_TO_POSITION);
                robot.right_front_motor.setMode(RUN_TO_POSITION);

                // start motion.
                speed = Range.clip(abs(speed), 0.0, 1.0);
                robot.driveToLEFTandRIGHT(speed, speed);


                // keep looping while we are still active, and BOTH motors are running.
                while (opModeIsActive() && (robot.left_back_motor.isBusy() && robot.left_front_motor.isBusy() &&
                        robot.right_back_motor.isBusy() && robot.right_front_motor.isBusy())) {

                    // adjust relative speed based on heading error.
                    error = getError(angle);
                    steer = getSteer(error, P_DRIVE_COEFF);

                    // if driving in reverse, the motor correction also needs to be reversed
                    if (distance < 0) {
                        steer *= -1.0;
                    }
                    frontSpeed = speed + steer;
                    backSpeed = speed - steer;

                    // Normalize speeds if either one exceeds +/- 1.0;
                    max = Math.max(abs(backSpeed), abs(frontSpeed));
                    if (max > 1.0) {
                        backSpeed /= max;
                        frontSpeed /= max;
                    }


                    robot.driveToLEFTandRIGHT(backSpeed, frontSpeed);


                    // Display drive status for the driver.
                    telemetry.addData("Err/St", "%5.1f/%5.1f", error, steer);
                    telemetry.addData("Target", "%7d:%7d", newLeftFrontTarget, newRightFrontTarget, newLeftBackTarget, newRightBackTarget);
                    telemetry.addData("Actual", "%7d:%7d", robot.left_front_motor.getCurrentPosition(), robot.right_front_motor.getCurrentPosition(), robot.left_back_motor.getCurrentPosition(), robot.right_back_motor.getCurrentPosition());
                    telemetry.update();
                }


                // Stop all motion;
                robot.fullDriving(0, 0);

            }
        } else if (direction == gyroDriveDirection.DIAGONALLEFT) {
            distance = (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
            newRightFrontTarget = robot.right_front_motor.getCurrentPosition() + distance;
            newLeftBackTarget = robot.left_back_motor.getCurrentPosition() + distance;

            // Set Target and Turn On RUN_TO_POSITION
            robot.right_front_motor.setTargetPosition(newRightFrontTarget);
            robot.left_back_motor.setTargetPosition(newLeftBackTarget);

            robot.left_back_motor.setMode(RUN_TO_POSITION);
            robot.right_front_motor.setMode(RUN_TO_POSITION);

            // start motion.
            speed = Range.clip(abs(speed), 0.0, 1.0);
            robot.driveToLEFTandRIGHT(speed, speed);

            if (opModeIsActive()) {
                distance = (int) (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
                if (distance < 0) {
                    speed = -speed;
                }
                while (opModeIsActive() && robot.left_front_motor.isBusy() && robot.left_back_motor.isBusy()){

                    // adjust relative speed based on heading error.
                    error = getError(angle);
                    steer = getSteer(error, P_DRIVE_COEFF);

                    // if driving in reverse, the motor correction also needs to be reversed
                    if (distance < 0)
                        steer *= -1.0;

                    leftSpeed = speed - steer;
                    rightSpeed = speed + steer;

                    // Normalize speeds if either one exceeds +/- 1.0;
                    max = Math.max(abs(leftSpeed), abs(rightSpeed));
                    if (max > 1.0) {
                        leftSpeed /= max;
                        rightSpeed /= max;
                    }

                    robot.left_back_motor.setPower(leftSpeed);
                    robot.right_front_motor.setPower(-rightSpeed);

                    // Display drive status for the driver.
                    telemetry.addData("Err/St", "%5.1f/%5.1f", error, steer);
                    telemetry.addData("Target", "%7d:%7d", newRightFrontTarget, newLeftBackTarget);
                    telemetry.addData("Actual", "%7d:%7d", robot.right_front_motor.getCurrentPosition(), robot.left_back_motor.getCurrentPosition());
                    telemetry.addData("Speed", "%5.2f:%5.2f", leftSpeed, rightSpeed);
                    telemetry.update();
                }

            }
                // Stop all motion;
                robot.fullDriving(0, 0);
                robot.drivingSetMode(RUN_USING_ENCODER);
        }
        else if (direction == gyroDriveDirection.DIAGONALRIGHT) {
            distance = (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
            newRightFrontTarget = robot.right_front_motor.getCurrentPosition() + distance;
            newLeftBackTarget = robot.left_back_motor.getCurrentPosition() + distance;

            // Set Target and Turn On RUN_TO_POSITION
            robot.right_front_motor.setTargetPosition(newRightFrontTarget);
            robot.left_back_motor.setTargetPosition(newLeftBackTarget);

            robot.left_back_motor.setMode(RUN_TO_POSITION);
            robot.right_front_motor.setMode(RUN_TO_POSITION);

            // start motion.
            speed = Range.clip(abs(speed), 0.0, 1.0);
            robot.driveToLEFTandRIGHT(speed, speed);

            if (opModeIsActive()) {
                distance = (int) (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
                if (distance < 0) {
                    speed = -speed;
                }
            }
            while (opModeIsActive() && robot.left_front_motor.isBusy() && robot.right_back_motor.isBusy()) {

                // adjust relative speed based on heading error.
                error = getError(angle);
                steer = getSteer(error, P_DRIVE_COEFF);

                // if driving in reverse, the motor correction also needs to be reversed
                if (distance < 0)
                    steer *= -1.0;

                leftSpeed = speed - steer;
                rightSpeed = speed + steer;

                // Normalize speeds if either one exceeds +/- 1.0;
                max = Math.max(abs(leftSpeed), abs(rightSpeed));
                if (max > 1.0) {
                    leftSpeed /= max;
                    rightSpeed /= max;
                }

                robot.left_front_motor.setPower(leftSpeed);
                robot.right_back_motor.setPower(-rightSpeed);

                // Display drive status for the driver.
                telemetry.addData("Err/St", "%5.1f/%5.1f", error, steer);
                telemetry.addData("Target", "%7d:%7d", newRightFrontTarget, newLeftBackTarget);
                telemetry.addData("Actual", "%7d:%7d", robot.right_front_motor.getCurrentPosition(), robot.left_back_motor.getCurrentPosition());
                telemetry.addData("Speed", "%5.2f:%5.2f", leftSpeed, rightSpeed);
                telemetry.update();
            }
            // Stop all motion;
            robot.fullDriving(0, 0);
            robot.drivingSetMode(RUN_USING_ENCODER);
        }
    }
    public void gyroTurn(double speed, double angle) {
        angle += gyroGetAngle();

        robot.drivingSetMode(RUN_USING_ENCODER);
        // keep looping while we are still active, and not on heading.
        while (opModeIsActive() && !onHeading(speed, angle, P_TURN_COEFF)) {
        }
    }


    boolean onHeading(double speed, double angle, double PCoeff) {
        double error;
        double steer;
        boolean onTarget = false;
        double leftSpeed;
        double rightSpeed;


        // determine turn power based on +/- error
        error = getError(angle);

        if (abs(error) <= HEADING_THRESHOLD) {
            steer = 0.0;
            leftSpeed = 0.0;
            rightSpeed = 0.0;
            onTarget = true;
            telemetry.addData("status", "on target");
            telemetry.update();
        } else {
            steer = getSteer(error, PCoeff);
            rightSpeed = speed * steer;
            leftSpeed = -rightSpeed;
            telemetry.addData("Target", "%5.2f", angle);
            telemetry.addData("Err/St", "%5.2f/%5.2f", error, steer);
            telemetry.addData("Speed.", "%5.2f:%5.2f", leftSpeed, rightSpeed);
            telemetry.update();
        }

        // Send desired speeds to motors.
        robot.fullDriving(leftSpeed, rightSpeed);

        // Display it for the driver.

        return onTarget;
    }


    public double getError(double targetAngle) {

        double robotError;
        // calculate error in -179 to +180 range  (
        double angle = gyroGetAngle();
        robotError = targetAngle - angle;

        while (robotError > 180 && opModeIsActive()) {
            robotError -= 360;
        }
        while (robotError <= -180 && opModeIsActive()) {
            robotError += 360;
        }
        return robotError;
    }


    public double getSteer(double error, double PCoeff) {
        return Range.clip(error * PCoeff, -1, 1);
    }

    private GoldPos findGoldPosition(findGoldPosZone findZone) {
        int width = 0;
        int goldMineral1X = -1;
        int goldMineral2X = -1;
        int goldMineral3X = -1;
        int silverMineral1X = -1;
        int silverMineral2X = -1;
        updatedRecognitions = tfod.getUpdatedRecognitions();
        if (updatedRecognitions != null) {
            telemetry.addData("# Object Detected", updatedRecognitions.size());

            if (updatedRecognitions.size() >= 2) {

                for (Recognition recognition : updatedRecognitions) {
                    if (recognition.getLabel().equals(LABEL_GOLD_MINERAL)) {
                        telemetry.addData("Gold", recognition.getTop());
                        if (goldMineral1X == -1) {

                            goldMineral1X = (int) recognition.getTop();
                        } else if (goldMineral2X == -1) {
                            goldMineral2X = (int) recognition.getTop();
                        } else {
                            goldMineral3X = (int) recognition.getTop();
                        }
                    } else {
                        telemetry.addData("Silver", recognition.getTop());
                        if (silverMineral1X == -1) {
                            silverMineral1X = (int) recognition.getTop();
                        } else {
                            silverMineral2X = (int) recognition.getTop();
                        }
                    }

                }
                goldMineral1X = vuforiaImprovement(goldMineral1X, goldMineral2X, goldMineral3X, silverMineral1X, silverMineral2X);
                if (findZone == findGoldPosZone.Regular) {
                    if (goldMineral1X != -1 && silverMineral1X != -1) {
                        telemetry.addData("Gold", "Silver");
                        if (goldMineral1X < silverMineral1X) {
                            goldPos = GoldPos.Left;
                            telemetry.addData("Detected ", "left");
                        } else {
                            goldPos = GoldPos.Center;
                            telemetry.addData("Detected ", "center");
                        }

                    } else if (goldMineral1X == -1 && silverMineral1X != -1 && silverMineral2X != -1) {
                        telemetry.addData("Detected ", "right");
                        return GoldPos.Right;
                    }
                }
            } else if (updatedRecognitions.size() == 1) {
                for (Recognition recognition : updatedRecognitions) {
                    width = recognition.getImageWidth();
                    if (recognition.getLabel().equals(LABEL_GOLD_MINERAL)) {
                        telemetry.addData("Detected", "gold");
                        goldMineral1X = (int) recognition.getTop();
                    }
                }
                if (findZone == findGoldPosZone.Regular) {
                    if (goldMineral1X != -1) {
                        if (goldMineral1X > width / 2) {
                            telemetry.addData("Detected ", "center");
                            return GoldPos.Center;

                        } else {
                            telemetry.addData("Detected ", "left");
                            return GoldPos.Left;
                        }
                    } else {
                        /**return GoldPos.tryRight;*/
                        telemetry.addData("Detected ", "right");
                        return GoldPos.Right;
                    }
                }
            }
        }
        return goldPos;
    }


    private void initVuforia() {
        /*
         * Configure Vuforia by creating a Parameter object, and passing it to the Vuforia engine.
         */
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters();

        parameters.vuforiaLicenseKey = VUFORIA_KEY;
        parameters.cameraDirection = VuforiaLocalizer.CameraDirection.FRONT;

        //  Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);

    }

    private void initTfod() {
        int tfodMonitorViewId = hardwareMap.appContext.getResources().getIdentifier(
                "tfodMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        TFObjectDetector.Parameters tfodParameters = new TFObjectDetector.Parameters(tfodMonitorViewId);
        tfod = ClassFactory.getInstance().createTFObjectDetector(tfodParameters, vuforia);
        tfod.loadModelFromAsset(TFOD_MODEL_ASSET, LABEL_GOLD_MINERAL, LABEL_SILVER_MINERAL);
    }


    private void climbDown(){
        robot.team_marker_servo.setPosition(0);
        robot.arm_motors(-0.5);
        armOpeningEncoder(1, 10);
        robot.arm_motors(0);
        robot.team_marker_servo.setPosition(0);
        runtime.reset();
        while(runtime.milliseconds() < 1000 && opModeIsActive()){
            robot.arm_motors(1);
        }
        robot.arm_motors(0);
        gyroDrive(1, -5, 0,0, gyroDriveDirection.FORWARDandBACKWARD);
        gyroDrive(1, -5, 0,0, gyroDriveDirection.LEFTandRIGHT);
        //goldPos=lookForMineral();
        goldPos=GoldPos.Left;
    }


    private void goToCrater() {



    }

    private int vuforiaImprovement(int goldMineral1X, int goldMineral2X, int goldMineral3X, int silverMineral1X, int silverMineral2X) {
        int range = 100;
        telemetry.addData("gold1: ", goldMineral1X);
        telemetry.addData("gold2: ", goldMineral2X);
        telemetry.addData("gold3: ", goldMineral3X);
        telemetry.addData("silver1: ", silverMineral1X);
        telemetry.addData("silver2: ", silverMineral2X);
        if (silverMineral1X != -1) {
            if (abs(silverMineral1X - goldMineral1X) < range) {
                goldMineral1X = -1;
            }
            if (abs(silverMineral1X - goldMineral2X) < range) {
                goldMineral2X = -1;
            }
            if (abs(silverMineral1X - goldMineral3X) < range) {
                goldMineral3X = -1;
            }
        }
        if (silverMineral2X != -1) {
            if (abs(silverMineral2X - goldMineral1X) < range) {
                goldMineral1X = -1;
            }
            if (abs(silverMineral2X - goldMineral2X) < range) {
                goldMineral2X = -1;
            }
            if (abs(silverMineral2X - goldMineral3X) < range) {
                goldMineral3X = -1;
            }
        }
        if (goldMineral1X == -1) {
            if (goldMineral2X != -1) {
                goldMineral1X = goldMineral2X;
            } else if (goldMineral3X != -1) {
                goldMineral1X = goldMineral3X;
            }
        }
        telemetry.addData("gold1 end: ", goldMineral1X);
        return goldMineral1X;
    }

    public float gyroGetAngle() {
        //telemetry.addData("y: ", robot.gyro.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.YZX, AngleUnit.DEGREES).firstAngle);
        telemetry.addData("z: ", robot.gyro.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.YZX, AngleUnit.DEGREES).secondAngle);
        //telemetry.addData("x: ", robot.gyro.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.YZX, AngleUnit.DEGREES).thirdAngle);
        return robot.gyro.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES).firstAngle;
    }
    private void putTeamMarker() {
        gyroDrive(1,15,0,0,gyroDriveDirection.FORWARDandBACKWARD);
        gyroDrive(1,115,-45,0,gyroDriveDirection.LEFTandRIGHT);
        gyroTurn(1, -20);
        pickUpMineral();
        gyroDrive(1,-130,-10,0,gyroDriveDirection.FORWARDandBACKWARD);
        robot.team_marker_servo.setPosition(0);
        gyroDrive(1,100,0,0,gyroDriveDirection.FORWARDandBACKWARD);
        robot.team_marker_servo.setPosition(1);
        gyroDrive(1,-28,0,0,gyroDriveDirection.LEFTandRIGHT);
        gyroTurn(1, 45);
        robot.arm_motors(0.5);
        gyroDrive(1,-70,15,0,gyroDriveDirection.LEFTandRIGHT);
        armEncoder(1,3000);
        robot.arm_motors(0.25);
        armOpeningEncoder(1,45);
        robot.mineral_keeper_servo.setPosition(1);
    }

    public void pickUpMineral(){
        robot.arm_motors(-0.15);
        armOpeningEncoder(1,30);
        robot.arm_motors(0);
        robot.arm_collecting_system.setPower(-1);
        armOpeningEncoder(1,10);
        robot.arm_collecting_system.setPower(0);
        robot.arm_motors(0.15);
        armOpeningEncoder(1,-30);
        robot.arm_motors(0);
    }
    private GoldPos lookForMineral() {
        GoldPos goldPosition = GoldPos.None;
        runtime.reset();
        while (runtime.milliseconds() < 1000) {
            if (goldPosition == GoldPos.None) {
                goldPosition = findGoldPosition(findGoldPosZone.Regular);
                telemetry.update();
            } else {
                break;
            }
        }
        return goldPosition;
    }
    public void sampling(GoldPos goldPosition){
        gyroDrive(1,10,0,0,gyroDriveDirection.FORWARDandBACKWARD);
        switch (goldPosition){
            case Left:
                gyroDrive(1, 80, 0,0, gyroDriveDirection.DIAGONALLEFT);
                sleep(100);
                gyroDrive(1, -80, 0,0, gyroDriveDirection.DIAGONALLEFT);
                break;
            case Right:
                gyroDrive(1, 80, 0,0, gyroDriveDirection.DIAGONALRIGHT);
                sleep(100);
                gyroDrive(1, -80, 0,0, gyroDriveDirection.DIAGONALRIGHT);
                break;
            case Center:
                gyroDrive(1, 38, 0,0, gyroDriveDirection.FORWARDandBACKWARD);
                sleep(200);
                gyroDrive(1, -38, 0,0, gyroDriveDirection.FORWARDandBACKWARD);
                break;
        }
    }
    public void OutPutMineral(){
        runtime.reset();
        while(runtime.seconds()<1.5){
            robot.arm_collecting_system.setPower(1);
        }
        robot.arm_collecting_system.setPower(0);
    }
    public void InPutMineral(){
        runtime.reset();
        while(runtime.seconds()<1.5){
            robot.arm_collecting_system.setPower(-1);
        }
        robot.arm_collecting_system.setPower(0);
    }
    public void armOpeningEncoder(double speed, double distance) {
        ErrorOnArmOpen=false;
        robot.arm_opening_system.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        robot.arm_opening_system.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);


        int Target = (int) (distance * (COUNTS_PER_MOTOR_NEVEREST40 / (Pi.getNumber() * PULLEY_DIAMETER_CM)));
        Target += robot.arm_opening_system.getCurrentPosition();
        int StartPos=robot.arm_opening_system.getCurrentPosition();
        Target *= -1;
        robot.arm_opening_system.setTargetPosition(Target);
        robot.arm_opening_system.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        runtime.reset();
        while (abs(Target) > abs(robot.arm_opening_system.getCurrentPosition())) {
            telemetry.addData("current:", robot.arm_opening_system.getCurrentPosition());
            telemetry.addData("target:", Target);
            telemetry.update();
            if (Target < robot.arm_opening_system.getCurrentPosition()) {
                robot.arm_opening_system.setPower(speed);
            }
            if (Target > robot.arm_opening_system.getCurrentPosition()) {
                robot.arm_opening_system.setPower(-speed);
            }
            if(runtime.seconds()>0.5 && robot.IntInRange(StartPos-30,StartPos+30,robot.arm_opening_system.getCurrentPosition())){
                ErrorOnArmOpen=true;
                while(runtime.seconds()<5) {
                    telemetry.addData("ERROR", "ARM_OPENING_SYSTEM");
                    telemetry.update();
                }
                break;
            }
        }
        robot.arm_opening_system.setPower(0);
    }

    public void armEncoder(double speed, double Target) {
        robot.arm_motor_2.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        robot.arm_motor_2.setTargetPosition((int)Target);
        telemetry.addData("target", Target);
        telemetry.addData("current", robot.arm_motor_2.getCurrentPosition());
        telemetry.update();
        if(robot.arm_motor_2.getCurrentPosition() < (int) Target) {
            while (abs(robot.arm_motor_2.getCurrentPosition()) < abs((int) Target) && opModeIsActive()) {
                telemetry.addData("current:", robot.arm_motor_2.getCurrentPosition());
                telemetry.addData("target:", Target);
                telemetry.update();
                robot.arm_motor_2.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
                if (Target > robot.arm_motor_2.getCurrentPosition()) {
                    robot.arm_motors(speed);
                }
                if (Target < robot.arm_motor_2.getCurrentPosition()) {
                    robot.arm_motors(-speed);
                }
                robot.arm_motor_2.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                telemetry.addData("current1:", robot.arm_motor_2.getCurrentPosition());
                telemetry.addData("target1:", Target);
                telemetry.update();
            }
        }
        else{
            while (robot.MinimumHight.getState()&& abs(robot.arm_motor_2.getCurrentPosition()) < abs((int) Target) && opModeIsActive()) {
                telemetry.addData("current:", robot.arm_motor_2.getCurrentPosition());
                telemetry.addData("target:", Target);
                telemetry.update();
                robot.arm_motor_2.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
                if (Target > robot.arm_motor_2.getCurrentPosition()) {
                    robot.arm_motors(speed);
                }
                if (Target < robot.arm_motor_2.getCurrentPosition()) {
                    robot.arm_motors(-speed);
                }
                robot.arm_motor_2.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
                telemetry.addData("current1:", robot.arm_motor_2.getCurrentPosition());
                telemetry.addData("target1:", Target);
                telemetry.update();
            }
        }
        robot.arm_motors(0);
    }
    public void FreeFlowDrive(double Degree){

        DriveY = -gamepad1.left_stick_y;
        DriveX = gamepad1.left_stick_x;


        if(TurnPower!=0) {
            robot.fullDriving(TurnPower, -TurnPower);
        }
        telemetry.addData("DriveX",DriveX);
        telemetry.addData("DriveY",DriveY);

    }
}