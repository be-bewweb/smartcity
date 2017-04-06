package be.info.unamur.actors

/** Messages case classes for pattern matching.
  *
  * @author Noé Picard
  */
trait Messages
case class Init(port: Int*) extends Messages
case class Start() extends Messages
case class Stop() extends Messages
case class SetGreen() extends Messages
case class SetRed() extends Messages
case class SetOn() extends Messages
case class SetOff() extends Messages
case class SwitchOn() extends Messages
case class SwitchOff() extends Messages
case class SecondaryCarComing() extends Messages
case class Blink() extends Messages
case class Close() extends Messages
case class Pedestrian() extends Messages
case class OpenBarrier() extends Messages
