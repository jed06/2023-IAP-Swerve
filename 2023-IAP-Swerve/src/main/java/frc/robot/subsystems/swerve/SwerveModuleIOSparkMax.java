// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.swerve;

import com.ctre.phoenix.sensors.AbsoluteSensorRange;
import com.ctre.phoenix.sensors.CANCoder;
import com.ctre.phoenix.sensors.SensorInitializationStrategy;
import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import com.revrobotics.CANSparkMaxLowLevel.PeriodicFrame;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Constants;
import frc.robot.Constants.ModuleConstants;

/**
 * Implements the SwerveModuleIO interface with two CANSparkMaxes. Uses a
 * CANCoder for turning and PID control using the 20 ms controller provided by
 * WPILib.
 * 
 * @author Aric Volman
 */
public class SwerveModuleIOSparkMax implements SwerveModuleIO {
    private CANSparkMax driveSparkMax;
    private CANSparkMax turnSparkMax;

    private SparkMaxPIDController drivePID;
    private PIDController turnPID;

    private RelativeEncoder driveEncoder;
    private CANCoder turnEncoder;

    // Variables to store voltages of motors - REV stuff doesn't like getters
    private double driveVolts = 0.0;
    private double turnVolts = 0.0;

    double offset;

    // Object to hold swerve module state
    private SwerveModuleState state = new SwerveModuleState(0.0, new Rotation2d(0.0));

    // Might not need offset if using CANCoders
    // private double angularOffset = 0.0;

    private int num = 0;

    /**
     * @param num               Module number
     * @param driveID           CAN ID for drive motor
     * @param turnID            CAN ID for turn motor
     * @param turnCANCoderID    CAN ID for CANCoder
     * @param turnEncoderOffset Offset in degrees for module (from -180 to 180)
     * @author Aric Volman
     */
    public SwerveModuleIOSparkMax(int num, int driveID, int turnID, int turnCANCoderID, double turnEncoderOffset, boolean invert) {
        


        turnEncoder = new CANCoder(turnCANCoderID);
        turnEncoder.configFactoryDefault();

        offset = turnEncoderOffset;

        // Construct CANSparkMaxes
        driveSparkMax = new CANSparkMax(driveID, MotorType.kBrushless);
        turnSparkMax = new CANSparkMax(turnID, MotorType.kBrushless);

                // Reset to defaults
                driveSparkMax.restoreFactoryDefaults();
                turnSparkMax.restoreFactoryDefaults();
        turnPID = new PIDController(0, 0, 0);

        turnSparkMax.setInverted(true);

        // Initialize encoder and PID controller
        driveEncoder = driveSparkMax.getEncoder();
        drivePID = driveSparkMax.getPIDController();
        drivePID.setFeedbackDevice(driveEncoder);

        // Set conversion factors
        driveEncoder.setPositionConversionFactor(ModuleConstants.drivingEncoderPositionFactor);
        driveEncoder.setVelocityConversionFactor(ModuleConstants.velocityPositionFactor);

        // Set SPARK MAX PIDF
        // More advantageous due to 1 KHz cycle (can ramp up action quicker)
        drivePID.setP(ModuleConstants.drivekP);
        drivePID.setI(ModuleConstants.drivekI);
        drivePID.setD(ModuleConstants.drivekD);
        drivePID.setFF(ModuleConstants.drivekF);
        drivePID.setOutputRange(-1, 1);

        driveSparkMax.setInverted(invert);
       // driveEncoder.setInverted(invert);

        driveSparkMax.setIdleMode(CANSparkMax.IdleMode.kBrake);
        turnSparkMax.setIdleMode(CANSparkMax.IdleMode.kBrake);
        driveSparkMax.setSmartCurrentLimit(ModuleConstants.driveCurrentLimit);
        turnSparkMax.setSmartCurrentLimit(ModuleConstants.turnCurrentLimit);

        driveSparkMax.setPeriodicFramePeriod(PeriodicFrame.kStatus3, 65535);
        turnSparkMax.setPeriodicFramePeriod(PeriodicFrame.kStatus3, 65535);

        // Only useful for encoder
        // turnSparkMax.setPeriodicFramePeriod(PeriodicFrame.kStatus5, 20);

        driveEncoder.setPosition(0);

        // Set position of encoder to absolute mode
        turnEncoder.setPositionToAbsolute();
        turnEncoder.configSensorInitializationStrategy(SensorInitializationStrategy.BootToAbsolutePosition);
        turnEncoder.configAbsoluteSensorRange(AbsoluteSensorRange.Unsigned_0_to_360);
        //turnEncoder.configMagnetOffset(turnEncoderOffset);

        turnPID.setP(Constants.ModuleConstants.turnkP);
        turnPID.setI(Constants.ModuleConstants.turnkI);
        turnPID.setD(Constants.ModuleConstants.turnkD);

        // Continous input jumping from 0 to 2*PI
        // Not advisable for Derivative constant
        turnPID.enableContinuousInput(0, 2*Math.PI);

        this.state.angle = new Rotation2d(getTurnPositionInRad());

        this.num = num;

    }

    public double getTurnPositionInRad() {
        // Divide by 1.0, as CANCoder has direct measurement of output
        return Units.degreesToRadians(turnEncoder.getAbsolutePosition() - offset) / ModuleConstants.CANCoderGearRatio;
    }

    public void setDesiredState(SwerveModuleState state) {
        // Optimize state so that movement is minimized
        state = SwerveModuleState.optimize(state, new Rotation2d(getTurnPositionInRad()));

        // Cap setpoints at max speeds for safety
        state.speedMetersPerSecond = MathUtil.clamp(state.speedMetersPerSecond,
                -Constants.ModuleConstants.maxFreeWheelSpeedMeters, Constants.ModuleConstants.maxFreeWheelSpeedMeters);

        // Set reference of drive motor's PIDF internally in SPARK MAX
        // This automagically updates at a 1 KHz rate
        drivePID.setReference(state.speedMetersPerSecond, CANSparkMax.ControlType.kVelocity);

        // Set setpoint of WPILib PID controller for turning
        this.turnPID.setSetpoint(state.angle.getRadians());

        // Calculate PID in motor power (from -1.0 to 1.0)
        double turnOutput = MathUtil.clamp(this.turnPID.calculate(getTurnPositionInRad()), -1.0, 1.0);

        // Set voltage of SPARK MAX
        turnSparkMax.setVoltage(turnOutput * 12.0);

        // Set internal state as passed-in state
        this.state = state;

    }
//commit
    public SwerveModuleState getDesiredState() {
        // Returns module state
        return this.state;
    }

    public SwerveModuleState getActualModuleState() {
        double velocity = this.driveEncoder.getVelocity();
        double rotation = this.getTurnPositionInRad();
        return new SwerveModuleState(velocity, Rotation2d.fromRadians(rotation));
    }

    public void setDriveVoltage(double volts) {
        driveSparkMax.setVoltage(volts);
        this.driveVolts = volts;
    }

    public void setTurnVoltage(double volts) {
        turnSparkMax.setVoltage(volts);
        this.turnVolts = volts;
    }

    public void setDriveBrakeMode(boolean enable) {
        if (enable) {
            driveSparkMax.setIdleMode(IdleMode.kBrake);
        } else {
            driveSparkMax.setIdleMode(IdleMode.kCoast);
        }
    }

    public void setTurnBrakeMode(boolean enable) {
        if (enable) {
            turnSparkMax.setIdleMode(IdleMode.kBrake);
        } else {
            turnSparkMax.setIdleMode(IdleMode.kCoast);
        }
    }

    public SwerveModulePosition getPosition() {
        double position = driveEncoder.getPosition();
        double rotation = this.getTurnPositionInRad();
        return new SwerveModulePosition(position, new Rotation2d(rotation));
    }

    public void resetEncoders() {
        // Resets only drive encoder
        driveEncoder.setPosition(0.0);
    }

    public void updateTelemetry() {
        SmartDashboard.putNumber("Max Free Speed", ModuleConstants.maxFreeWheelSpeedMeters);
        SmartDashboard.putNumber("Wheel Displacement #" + this.num, getPosition().distanceMeters);
  
        // Show turning position and setpoints
        SmartDashboard.putNumber("Turn Pos Degrees #" + this.num,
              Units.radiansToDegrees(getTurnPositionInRad()));
        SmartDashboard.putNumber("Raw Turn Pos #" + num, getTurnPositionInRad());
        SmartDashboard.putNumber("Setpoint Turn Pos #" + this.num, state.angle.getRadians());
  
        // Show driving velocity and setpoints
        SmartDashboard.putNumber("Drive Vel #" + this.num, driveEncoder.getVelocity());
        SmartDashboard.putNumber("Setpoint Drive Vel #" + this.num, state.speedMetersPerSecond);
  
        // Output of driving
        SmartDashboard.putNumber("Turn Volts #" + this.num, this.turnVolts);
        SmartDashboard.putNumber("Drive Volts #" + this.num, this.driveVolts);
  
        // Get RPMs
        SmartDashboard.putNumber("Turn RPM #" + this.num, (turnEncoder.getVelocity()/360.0)*60.0);
        SmartDashboard.putNumber("Drive RPM #" + this.num, driveEncoder.getVelocity()/Constants.ModuleConstants.drivingEncoderPositionFactor);
     }

    public int getNum() {
        return num;
    }

}