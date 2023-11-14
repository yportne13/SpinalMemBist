package main

import spinal.core._
import spinal.lib._
import MemBist._
import _root_.scala.collection.mutable.ArrayBuffer

class Bist extends BistCtrl {
  val io = new Bundle {
    val en = in Bool()
  }

  override def tasks: Unit = {
    mems.foreach{case (count, width, bundle, memcomponent, task) =>
      this.rework {
        val bist = master(cloneOf(bundle))
        bist.wr_addr := 0
        bist.wr_clk  := ClockDomain.current.readClockWire
        bist.wr_data := 0
        bist.wr_en   := io.en
        bist.wr_mask := 0
        bist.rd_addr := 0
        bist.rd_clk  := ClockDomain.current.readClockWire
        bist.rd_en   := io.en
        task(bist, memcomponent)
      }
    }
  }

  
}

class toplevel extends Component {
  val io = new Bundle {
    val bist_en = in Bool()
    val slv = slave Stream(Bits(8 bits))
    val mst = master Stream(Bits(8 bits))
    val we = in Bool()
    val waddr = in UInt(4 bits)
    val win = in Bits(8 bits)
    val raddr = in UInt(4 bits)
    val rout = out Bits(8 bits)
  }

  val bist_ctrl = new Bist
  bist_ctrl.io.en := io.bist_en

  val mems = (0 until 4).map(idx => Mem(Bits(8 bits), 16))
  mems.foreach(mem => mem.write(io.waddr, io.win, io.we))

  val sub = new submodule
  sub.io.slv << io.slv
  io.mst << sub.io.mst
  sub.io.waddr := io.waddr
  sub.io.win := io.win
  sub.io.we := io.we
  sub.io.raddr := io.raddr

  io.rout := mems.map(mem => mem.readSync(io.raddr)).reduce((a, b) => a | b) | sub.io.rout

}

class submodule extends Component {
  val io = new Bundle {
    val slv = slave Stream(Bits(8 bits))
    val mst = master Stream(Bits(8 bits))
    val we = in Bool()
    val waddr = in UInt(4 bits)
    val win = in Bits(8 bits)
    val raddr = in UInt(4 bits)
    val rout = out Bits(8 bits)
  }

  val mems = (0 until 4).map(idx => Mem(Bits(8 bits), 16))
  mems.foreach(mem => mem.write(io.waddr, io.win, io.we))
  io.rout := mems.map(mem => mem.readSync(io.raddr)).reduce((a, b) => a | b)

  io.mst << io.slv.queue(64)

}

object main extends App {
  SpinalConfig(
    memBlackBoxers = ArrayBuffer(new PhaseMemBlackBoxingDefault(), new GenBistCtrl)
  ).generateVerilog(new toplevel)
}
