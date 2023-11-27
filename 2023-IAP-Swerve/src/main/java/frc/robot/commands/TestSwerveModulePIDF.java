// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.subsystems.swerve.SingularModule;

public class TestSwerveModulePIDF extends CommandBase {

  private SingularModule moduleSubsystem;

  // Create suppliers as object references
  private double angle = 0.0;
  private double velocity = 0.0;

  private Joystick joy;

  /** Creates a new TestSwerveModule. */
  public TestSwerveModulePIDF(SingularModule module, Joystick joy) {
    // Use addRequirements() here to declare subsystem dependencies.
    this.moduleSubsystem = module;

    this.joy = joy;

    addRequirements(moduleSubsystem);
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    moduleSubsystem.module.setDesiredState(new SwerveModuleState());
  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {

    // Reset driving to 0.0 - left button (#10)
    if (joy.getRawButton(10)) {
      velocity = 0.0;
    }

    // Reset turning to 0.0 - right button (#11)
    if (joy.getRawButton(11)) {
      angle = 0.0;
    }

    // Driving to 1.0 - Left bumper (upper)
    if (joy.getRawButton(4)) {
      velocity = 1.0;
    }

    // Driving to 2.0 - Left trigger bumper (lower)
    if (joy.getRawButton(6)) {
      velocity = 2.0;
    }

    // Driving to 3.0 - 
    if (joy.getRawButton(5)) {
      velocity = 3.0;
    }

    // Driving to 4.0
    if (joy.getRawButton(7)) {
      velocity = 4.0;
    }

    // Driving to pi/4
    if (joy.getRawButton(0)) {
      angle = Math.PI/4;
    }

    // Driving to pi/2
    if (joy.getRawButton(1)) {
      angle = 2*Math.PI/4;
    }

    // Driving to 3*pi/4
    if (joy.getRawButton(2)) {
      angle = 3*Math.PI/4;
    }

    // Driving to 4*pi/4
    if (joy.getRawButton(3)) {
      angle = 4*Math.PI/4;
    }

    moduleSubsystem.module.setDesiredState(new SwerveModuleState(velocity, new Rotation2d(angle)));
  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
    moduleSubsystem.module.setDesiredState(new SwerveModuleState());
  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return false;
  }
}
