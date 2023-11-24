package main.ExtendSignal

import MemLib._
import spinal.core._
import spinal.lib._
import scala.collection.mutable.ArrayBuffer

class toplevel extends Component {
  val io = new Bundle {
    val bistEn = in Vec(Bool(), 4)
    val we = in Bool()
    val waddr = in UInt(4 bits)
    val win = in Bits(8 bits)
    val raddr = in UInt(4 bits)
    val rout = out Bits(8 bits)
  }

  val mems = (0 until 4).map(idx => Mem(Bits(8 bits), 9 + idx))
  mems.foreach(mem => mem.write(io.waddr, io.win, io.we))
  mems(0).bistEn(io.bistEn(3))
  mems(1).bistEn(io.bistEn(2))
  mems(2).bistEn(io.bistEn(1))
  mems(3).bistEn(io.bistEn(0))

  val sub = new submodule
  sub.io.bistEn := io.bistEn
  sub.io.waddr := io.waddr
  sub.io.win := io.win
  sub.io.we := io.we
  sub.io.raddr := io.raddr

  io.rout := mems.map(mem => mem.readSync(io.raddr)).reduce((a, b) => a | b) | sub.io.rout

}

class submodule extends Component {
  val io = new Bundle {
    val bistEn = in Vec(Bool(), 4)
    val we = in Bool()
    val waddr = in UInt(4 bits)
    val win = in Bits(8 bits)
    val raddr = in UInt(4 bits)
    val rout = out Bits(8 bits)
  }

  val mems = (0 until 4).map(idx => Mem(Bits(8 bits), 9 + idx))
  mems.foreach(mem => mem.write(io.waddr, io.win, io.we))
  mems.zip(io.bistEn).foreach{
    case (mem, bisten) => mem.bistEn(bisten)
  }
  io.rout := mems.map(mem => mem.readSync(io.raddr)).reduce((a, b) => a | b)

}

object main extends App {
  SpinalConfig(
    memBlackBoxers = ArrayBuffer(new PhaseMemBlackBoxingDefault())
  ).generateVerilog(new toplevel)
}
