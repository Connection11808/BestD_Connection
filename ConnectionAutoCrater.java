package org.firstinspires.ftc.teamcode;
import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.hardware.bosch.JustLoggingAccelerationIntegrator;
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
import static com.qualcomm.robotcore.hardware.DcMotor.RunMode.STOP_AND_RESET_ENCODER;

@Autonomous(name="Connection Auto Crater", group="Test")

public class ConnectionAutoCrater extends LinearOpMode {

    /* Declare OpMode members. */
    Hardware_Connection robot = new Hardware_Connection();


    static final double COUNTS_PER_MOTOR_NEVEREST40 = 1120;    // eg: NEVEREST40 Motor Encoder
    static final double DRIVE_GEAR_REDUCTION_NEVEREST40 = 1;     // This is < 1.0 if geared UP
    static final double WHEEL_DIAMETER_CM = 10.5360;     // For figuring circumference
    static final double PULLEY_DIAMETER_CM = 4;
    static final double STEER = 0.93; //friction coefficiant
    static final double COUNTS_PER_CM_ANDYMARK_WHEEL = ((COUNTS_PER_MOTOR_NEVEREST40 * DRIVE_GEAR_REDUCTION_NEVEREST40) / (WHEEL_DIAMETER_CM * Pi.getNumber()) * STEER);

    static final double COUNTS_PER_CM_OPENING = ((COUNTS_PER_MOTOR_NEVEREST40 * DRIVE_GEAR_REDUCTION_NEVEREST40) / PULLEY_DIAMETER_CM * Pi.getNumber());

    static final double COUNTS_PER_MOTOR_TETRIX = 1440;
    static final double ARM_GEAR_REDUCTION_TETRIX = 1 / 9;
    static final double TETRIX_MOTOR_ANGLES = COUNTS_PER_MOTOR_TETRIX * (float) ARM_GEAR_REDUCTION_TETRIX / 360.0;

    private ElapsedTime runtime = new ElapsedTime();
    private VuforiaLocalizer vuforia;
    private TFObjectDetector tfod;


    static final double HEADING_THRESHOLD = 1;      // As tight as we can make it with an integer gyro
    static final double P_TURN_COEFF = 0.1;     // Larger is more responsive, but also less stable
    static final double P_DRIVE_COEFF = 0.1;     // Larger is more responsive, but also less stable
    private static final String TFOD_MODEL_ASSET = "RoverRuckus.tflite";
    private static final String LABEL_GOLD_MINERAL = "Gold Mineral";
    private static final String LABEL_SILVER_MINERAL = "Silver Mineral";
    private static final String VUFORIA_KEY = "AdztnQD/////AAABmTi3BA0jg0pqo1JcP43m+HQ09hcSrJU5FcbzN8MIqJ5lqy9rZzpO8BQT/FB4ezNV6J8XJ6oWRIII5L18wKbeTxlfRahbV3DUl48mamjtSoJgYXX95O0zaUXM/awgtEcKRF15Y/jwmVB5NaoJ3XMVCVmmjkDoysLvFozUttPZKcZ4C9AUcnRBQYYJh/EBSmk+VISyjHZw28+GH2qM3Z2FnlAY6gNBNCHiQvj9OUQSJn/wTOyCeI081oXDBt0BznidaNk0FFq0V0Qh2a/ZiUiSVhsWOdaCudwJlzpKzaoDmxPDujtizvjmPR4JYYkmUX85JZT/EMX4KgoCb2WaYSGK7hkx5oAnY4QC72hSnO83caqF";

    protected enum gyroDriveDirection {
        LEFTandRIGHT,
        FORWARDandBACKWARD
    }


    protected enum motorType {
        ARM,
        DRIVE,
        OPENING_SYSTEM
    }


    protected enum GoldPos {
        Right,
        Left,
        Mid,
        None
    }

    GoldPos goldPos = GoldPos.None;

    public void runOpMode() {
        robot.init(hardwareMap);
        initVuforia();

        if (ClassFactory.getInstance().canCreateTFObjectDetector()) {
            initTfod();
        } else {
            telemetry.addData("Sorry!", "This device is not compatible with TFOD");
        }
        if (tfod != null)
            tfod.activate();

        telemetry.addData("status", "ready for start");
        telemetry.update();

        //waiting for the user to press start.
        waitForStart();


        climbDown();
        goldPos = GoldPos.None;
        while (goldPos == GoldPos.None && opModeIsActive()) {
            telemetry.addData(">","Serching For 2 Minerals");
            telemetry.update();
            findGoldPosition();

        }
        //our main code.
        goToMineral();
        //goToCrater();
    }


    public void gyroDrive(double speed,
                          double distance,
                          double angle,
                          gyroDriveDirection direction) {

        int newLeftFrontTarget;
        int newRightFrontTarget;
        int newLeftBackTarget;
        int newRightBackTarget;
        int moveCounts;
        double max;
        double error;
        double steer;
        double leftSpeed;
        double rightSpeed;
        double backSpeed;
        double frontSpeed;

        robot.gyro.initialize(robot.parameters);

        if (direction == gyroDriveDirection.FORWARDandBACKWARD) {
            telemetry.addData("gyroDrive", "gyroDrive");
            telemetry.update();

            // Ensure that the opmode is still active
            if (opModeIsActive()) {

                // Determine new target position, and pass to motor controller
                moveCounts = (int) (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
                newLeftFrontTarget = robot.left_front_motor.getCurrentPosition() + moveCounts;
                newRightFrontTarget = robot.right_front_motor.getCurrentPosition() + moveCounts;
                newRightBackTarget = robot.right_back_motor.getCurrentPosition() + moveCounts;
                newLeftBackTarget = robot.left_back_motor.getCurrentPosition() + moveCounts;

                // Set Target and Turn On RUN_TO_POSITION
                robot.left_front_motor.setTargetPosition(newLeftFrontTarget);
                robot.right_front_motor.setTargetPosition(newRightFrontTarget);
                robot.right_back_motor.setTargetPosition(newRightBackTarget);
                robot.left_back_motor.setTargetPosition(newLeftBackTarget);

                robot.left_front_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                robot.right_front_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                robot.right_back_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                robot.left_back_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);

                // start motion.
                speed = Range.clip(Math.abs(speed), 0.0, 1.0);
                robot.fullDriving(speed, -speed);

                // keep looping while we are still active, and BOTH motors are running.
                while (opModeIsActive() &&
                        (robot.left_back_motor.isBusy() && robot.left_front_motor.isBusy() &&
                                robot.right_back_motor.isBusy() && robot.right_front_motor.isBusy())) {

                    // adjust relative speed based on heading error.
                    error = getError(angle);
                    steer = getSteer(error, P_DRIVE_COEFF);

                    // if driving in reverse, the motor correction also needs to be reversed
                    if (distance < 0)
                        steer *= -1.0;

                    leftSpeed = speed - steer;
                    rightSpeed = speed + steer;

                    // Normalize speeds if either one exceeds +/- 1.0;
                    max = Math.max(Math.abs(leftSpeed), Math.abs(rightSpeed));
                    if (max > 1.0) {
                        leftSpeed /= max;
                        rightSpeed /= max;
                    }

                    robot.fullDriving(leftSpeed, -rightSpeed);

                    // Display drive status for the driver.
                    //telemetry.addData("Err/St", "%5.1f/%5.1f", error, steer);
                    telemetry.addData("Target", "%7d:%7d", newLeftFrontTarget, newRightFrontTarget, newLeftBackTarget, newRightBackTarget);
                    telemetry.addData("Actual", "%7d:%7d", robot.left_front_motor.getCurrentPosition(), robot.right_front_motor.getCurrentPosition(), robot.right_back_motor.getCurrentPosition(), robot.left_back_motor.getCurrentPosition());
                    //telemetry.addData("Speed", "%5.2f:%5.2f", leftSpeed, rightSpeed);
                    telemetry.update();
                }


                // Stop all motion;
                robot.fullDriving(0, 0);


            }

        }
        else if (direction == gyroDriveDirection.LEFTandRIGHT) {

            // Ensure that the opmode is still active
            if (opModeIsActive()) {

                // Determine new target position, and pass to motor controller
                moveCounts = (int) (distance * COUNTS_PER_CM_ANDYMARK_WHEEL);
                newLeftFrontTarget = robot.left_front_motor.getCurrentPosition() - moveCounts;
                newRightFrontTarget = robot.right_front_motor.getCurrentPosition() + moveCounts;
                newRightBackTarget = robot.right_back_motor.getCurrentPosition() - moveCounts;
                newLeftBackTarget = robot.left_back_motor.getCurrentPosition() + moveCounts;

                // Set Target and Turn On RUN_TO_POSITION
                robot.left_front_motor.setTargetPosition(newLeftFrontTarget);
                robot.right_front_motor.setTargetPosition(newRightFrontTarget);
                robot.right_back_motor.setTargetPosition(newRightBackTarget);
                robot.left_back_motor.setTargetPosition(newLeftBackTarget);

                robot.left_front_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                robot.right_front_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                robot.right_back_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
                robot.left_back_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);

                // start motion.
                speed = Range.clip(Math.abs(speed), 0.0, 1.0);
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
                    max = Math.max(Math.abs(backSpeed), Math.abs(frontSpeed));
                    if (max > 1.0) {
                        backSpeed /= max;
                        frontSpeed /= max;
                    }


                    robot.driveToLEFTandRIGHT(backSpeed, frontSpeed);


                    // Display drive status for the driver.
                    // telemetry.addData("Err/St", "%5.1f/%5.1f", error, steer);
                    telemetry.addData("Target", "%7d:%7d", newLeftFrontTarget, newRightFrontTarget, newLeftBackTarget, newRightBackTarget);
                    telemetry.addData("Actual", "%7d:%7d", robot.left_front_motor.getCurrentPosition(), robot.right_front_motor.getCurrentPosition(), robot.left_back_motor.getCurrentPosition(), robot.right_back_motor.getCurrentPosition());
                    //telemetry.addData("Speed", "%5.2f:%5.2f", backSpeed, frontSpeed);
                    telemetry.update();
                }


                // Stop all motion;
                robot.fullDriving(0, 0);

            }
        }
    }

    public void gyroTurn(double speed, double angle) {
        robot.gyro.initialize(robot.parameters);
        while (opModeIsActive() && !onHeading(speed, angle, P_TURN_COEFF)) {
            telemetry.update();
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

        if (Math.abs(error) <= HEADING_THRESHOLD) {
            steer = 0.0;
            leftSpeed = 0.0;
            rightSpeed = 0.0;
            onTarget = true;
        } else {
            steer = getSteer(error, PCoeff);
            rightSpeed = speed * steer;
            leftSpeed = -rightSpeed;
        }

        // Send desired speeds to motors.
        robot.fullDriving(leftSpeed, rightSpeed);

        // Display it for the driver.
        telemetry.addData("Target", "%5.2f", angle);
        telemetry.addData("Err/St", "%5.2f/%5.2f", error, steer);
        telemetry.addData("Speed.", "%5.2f:%5.2f", leftSpeed, rightSpeed);

        return onTarget;
    }


    public double getError(double targetAngle) {

        double robotError;
        // calculate error in -179 to +180 range  (
        robotError = targetAngle - robot.gyro.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES).firstAngle;

        while (robotError > 180 && opModeIsActive()) robotError -= 360;
        while (robotError <= -180 && opModeIsActive()) robotError += 360;
        return robotError;
    }

    public double getSteer(double error, double  PCoeff) {
        return Range.clip(error * PCoeff, -1, 1);
    }

    private void findGoldPosition() {
        List<Recognition> updatedRecognitions = tfod.getUpdatedRecognitions();
        goldPos = GoldPos.None;
        if (updatedRecognitions != null) {
            telemetry.addData("# Object Detected", updatedRecognitions.size());
            if (updatedRecognitions.size() == 2) {
                int goldMineralX = -1;
                int silverMineral1X = -1;
                int silverMineral2X = -1;
                for (Recognition recognition : updatedRecognitions) {
                    if (recognition.getLabel().equals(LABEL_GOLD_MINERAL)) {
                        goldMineralX = (int) recognition.getTop();
                    }
                    else if (silverMineral1X == -1) {
                        silverMineral1X = (int) recognition.getTop();
                    }
                    else {
                        silverMineral2X = (int) recognition.getTop();
                    }
                }
                if (goldMineralX != -1 && silverMineral1X != -1) {
                    if (goldMineralX < silverMineral1X) {
                        goldPos = GoldPos.Right;
                        telemetry.addData("position","gold is on left");

                    }
                    if (goldMineralX > silverMineral1X) {
                        goldPos = GoldPos.Mid;
                        telemetry.addData("position","gold is on mid");

                    }


                }

                if (goldMineralX == -1 && silverMineral1X != -1 && silverMineral2X != -1) {
                    goldPos = GoldPos.Left;
                    telemetry.addData("position","gold is on right");

                }
                runtime.reset();
                while(runtime.seconds()<3) {
                    telemetry.addData("goldMineralX:", goldMineralX);
                    telemetry.addData("Gold position:", goldPos);
                    telemetry.update();
                }
                }
        }


        if (tfod != null) {
            tfod.shutdown();
        }
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


    protected void climbDown() {
        //robot.arm_motors(0.5);
        encoderMove(1, -5, motorType.OPENING_SYSTEM);
        robot.arm_motors(-0.2);
        /*for(double armPower = -0.2; armPower < 0; armPower += 0.01) {
            robot.arm_motors(armPower);
        }*/
        runtime.reset();
        while(runtime.milliseconds() < 500 && opModeIsActive()){
            robot.arm_motors(-1);
        }
        robot.arm_motors(0);
        gyroTurn(0.7, 75);
        gyroDrive(0.3, -2, 0, gyroDriveDirection.LEFTandRIGHT);
        runtime.reset();
        while(runtime.milliseconds() < 1 && opModeIsActive()) {
            robot.arm_motors(0.7);
        }
        robot.arm_motors(0);
        //encoderMove(1, 20, motorType.OPENING_SYSTEM);
    }

    protected void craterParking() {
        robot.gyro.initialize(robot.parameters);
        gyroDrive(0.4, 2, 0, gyroDriveDirection.LEFTandRIGHT);
        gyroTurn(0.7, 90);

    }

    protected void goToMineral() {
        if (goldPos == GoldPos.Left) {
            gyroDrive(0.3,-20,0,gyroDriveDirection.LEFTandRIGHT);
            gyroDrive(0.7, -50, 0, gyroDriveDirection.FORWARDandBACKWARD);
            gyroDrive(0.6, -40, 0, gyroDriveDirection.LEFTandRIGHT);
            robot.fullDriving(0,0);
            telemetry.addData("Status","going to right");
        }
        else if (goldPos == GoldPos.Mid){
            gyroDrive(0.6,-70,0,gyroDriveDirection.LEFTandRIGHT);
            robot.fullDriving(0,0);
            telemetry.addData("Status","going mid");
        }
        else if (goldPos == GoldPos.Right){
            gyroDrive(0.3,-20,0,gyroDriveDirection.LEFTandRIGHT);
            gyroDrive(0.7,50,0,gyroDriveDirection.FORWARDandBACKWARD);
            gyroDrive(0.6, -40, 0, gyroDriveDirection.LEFTandRIGHT);
            telemetry.addData("Status","going to left");

        }
        else if (goldPos == GoldPos.None){
            telemetry.addData("Status","no mineral was found");
        }
        telemetry.update();
    }
    protected void goToCrater(){
        gyroTurn(0.5,-90);
        encoderMove(1,-40,motorType.OPENING_SYSTEM);
        runtime.reset();
        while(runtime.milliseconds() < 2000 && opModeIsActive()){
            robot.arm_collecting_system.setPower(-0.7);
        }
        robot.arm_collecting_system.setPower(0);
    }




    protected void encoderMove(double speed, double distance_OR_angles, motorType type) {
        int moveCounts;
        int leftFrontTarget;
        int leftBackTarget;
        int rightFrontTarget;
        int rightBackTarget;
        int armTarget;
        int openingTarget;


        if (type == motorType.DRIVE) {
            robot.left_back_motor.setMode(RUN_USING_ENCODER);
            robot.left_front_motor.setMode(RUN_USING_ENCODER);
            robot.right_back_motor.setMode(RUN_USING_ENCODER);
            robot.right_front_motor.setMode(RUN_USING_ENCODER);

            robot.left_back_motor.setMode(STOP_AND_RESET_ENCODER);
            robot.left_front_motor.setMode(STOP_AND_RESET_ENCODER);
            robot.right_back_motor.setMode(STOP_AND_RESET_ENCODER);
            robot.right_front_motor.setMode(STOP_AND_RESET_ENCODER);

            moveCounts = (int) (distance_OR_angles * COUNTS_PER_CM_ANDYMARK_WHEEL);
            leftFrontTarget = robot.left_front_motor.getCurrentPosition() + moveCounts;
            rightFrontTarget = robot.right_front_motor.getCurrentPosition() + moveCounts;
            rightBackTarget = robot.right_back_motor.getCurrentPosition() + moveCounts;
            leftBackTarget = robot.left_back_motor.getCurrentPosition() + moveCounts;

            robot.right_front_motor.setTargetPosition(rightFrontTarget);
            robot.right_back_motor.setTargetPosition(rightBackTarget);
            robot.left_front_motor.setTargetPosition(leftFrontTarget);
            robot.left_back_motor.setTargetPosition(leftBackTarget);

            robot.left_front_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            robot.right_front_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            robot.right_back_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            robot.left_back_motor.setMode(DcMotor.RunMode.RUN_TO_POSITION);

            robot.fullDriving(speed, -speed);
            while (opModeIsActive() && (robot.right_front_motor.isBusy() && robot.right_back_motor.isBusy() && robot.left_front_motor.isBusy() && robot.left_back_motor.isBusy())) {
            }
            robot.fullDriving(0, 0);

        }
        else if (type == motorType.ARM) {
            telemetry.addData("arm", "arm");
            telemetry.update();
            robot.arm_motor_2.setMode(RUN_USING_ENCODER);

            robot.arm_motor_2.setMode(STOP_AND_RESET_ENCODER);

            moveCounts = (int) (distance_OR_angles * TETRIX_MOTOR_ANGLES);
            armTarget = robot.arm_motor_2.getCurrentPosition() - moveCounts;

            robot.arm_motor_2.setTargetPosition(armTarget);

            robot.arm_motor_2.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            int counter = 0;
            robot.arm_motors(speed);
            while (opModeIsActive() && (robot.arm_motor_2.isBusy())){
                telemetry.addData("encoder: ", robot.arm_motor_2.getCurrentPosition());
                telemetry.addData("angles: ", moveCounts);
                telemetry.addData("counter: ", counter);
                telemetry.update();
                counter++;
            }
            robot.arm_motors(0);

        }
        else if (type == motorType.OPENING_SYSTEM) {
            robot.arm_opening_system.setMode(RUN_USING_ENCODER);
            robot.arm_opening_system.setMode(STOP_AND_RESET_ENCODER);

            moveCounts = (int) (distance_OR_angles * (COUNTS_PER_CM_OPENING/10));
            openingTarget = robot.arm_opening_system.getCurrentPosition() - moveCounts;

            robot.arm_opening_system.setTargetPosition(openingTarget);
            robot.arm_opening_system.setMode(RUN_TO_POSITION);

            robot.arm_opening_system.setPower(speed);
            while (opModeIsActive() && robot.arm_opening_system.isBusy()){
                telemetry.addData("opening", "opening");
                telemetry.update();
            }
            telemetry.addData("opening2", "opening2");
            telemetry.update();
            robot.arm_opening_system.setPower(0);


        }

    }
}




