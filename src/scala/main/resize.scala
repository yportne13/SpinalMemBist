package main

import spinal.core._
import _root_.scala.collection.mutable.ArrayBuffer
import spinal.core.internals.PhaseNetlist
import spinal.core.internals.PhaseContext
import spinal.core.internals.AssignmentStatement
import _root_.spinal.core.internals.Resize

// TODO: cannot check for tuple assign
class ResizeCheck extends PhaseNetlist {
  override def impl(pc: PhaseContext): Unit = {
    pc.topLevel.walkComponents(c => {
      c.dslBody.foreachStatements{
        case as: AssignmentStatement => {
          println(as)
          as.source match {
            case s: BaseType => {
              if(s.hasTag(tagAutoResize))
                println(s"--- ${as} ---")
              if(s.hasTag(tagAutoResize))
              as.target match {
                case t: BaseType => {
                  if(t.getBitsWidth < s.getBitsWidth)
                    PendingError(s"INVALID RESIZE (${t.getBitsWidth} bits <- ${s.getBitsWidth} bits) on ${as.toStringMultiLine} at \n${as.getScalaLocationLong}")
                }
                case _ =>
              }
            }
            case _ =>
          }
        }
        case _ =>
      }
    })
  }
}

object strictResize extends App {
  class sub extends Component {
    val io = new Bundle {
      val a = in UInt(8 bits)
      val b = in UInt(8 bits)
      val c = out UInt(7 bits)
    }

    io.c := (io.a +^ io.b).resized
  }
  class strictResize extends Component {
    val io = new Bundle {
      val a = in UInt(8 bits)
      val b = in UInt(8 bits)
      val c = out UInt(9 bits)
      val d = out UInt(7 bits)
    }

    io.c := (io.a +^ io.b)//.resized
    val s = new sub
    s.io.a := io.a
    s.io.b := io.b
    io.d := s.io.c//.resized

  }
  SpinalConfig(transformationPhases = ArrayBuffer(new ResizeCheck))
    .generateVerilog(new strictResize)
}
