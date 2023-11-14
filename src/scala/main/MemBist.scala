package MemBist

import spinal.core._
import spinal.core.internals.PhaseMemBlackBoxingWithPolicy
import spinal.core.internals.MemTopology
import spinal.core.internals.Expression
import spinal.lib._
import spinal.core.internals.PhaseNetlist
import spinal.core.internals.PhaseContext

abstract class BistCtrl extends Component {
  type Count = Int
  type Width = Int
  var mems: List[(Count, Width, BistBundle, Component, (BistBundle, Component) => Unit)] = List()

  def tasks: Unit
}

case class BistBundle(
  wrMaskWidth: Int,
  wrAddressWidth: Int,
  wrDataWidth: Int,
  rdAddressWidth: Int,
  rdDataWidth: Int
) extends Bundle with IMasterSlave {
  val wr_clk  = Bool()
  val wr_en   = Bool()
  val wr_mask = Bits(wrMaskWidth bits)
  val wr_addr = UInt(wrAddressWidth bit)
  val wr_data = Bits(wrDataWidth bit)
  val rd_clk  = Bool()
  val rd_en   = Bool()
  val rd_addr = UInt(rdAddressWidth bit)
  val rd_data = Bits(rdDataWidth bit)

  override def asMaster(): Unit = {
    out(wr_clk)
    out(wr_en)
    out(wr_mask)
    out(wr_addr)
    out(wr_data)
    out(rd_clk)
    out(rd_en)
    out(rd_addr)
    in (rd_data)
  }

}

class Ram_1w_1rs_bist(
  val wordWidth      : Int,
  val wordCount      : Int,
  val readUnderWrite : ReadUnderWritePolicy = dontCare,
  val technology     : MemTechnologyKind = auto,

  val wrClock        : ClockDomain,
  val wrAddressWidth : Int,
  val wrDataWidth    : Int,
  val wrMaskWidth    : Int = 1,
  val wrMaskEnable   : Boolean = false,

  val rdClock        : ClockDomain,
  val rdAddressWidth : Int,
  val rdDataWidth    : Int
) extends BlackBox {

  addGenerics(
    "wordCount"      -> Ram_1w_1rs_bist.this.wordCount,
    "wordWidth"      -> Ram_1w_1rs_bist.this.wordWidth,
    "clockCrossing"  -> (wrClock != rdClock),
    "technology"     -> Ram_1w_1rs_bist.this.technology.technologyKind,
    "readUnderWrite" -> Ram_1w_1rs_bist.this.readUnderWrite.readUnderWriteString,
    "wrAddressWidth" -> Ram_1w_1rs_bist.this.wrAddressWidth,
    "wrDataWidth"    -> Ram_1w_1rs_bist.this.wrDataWidth,
    "wrMaskWidth"    -> Ram_1w_1rs_bist.this.wrMaskWidth,
    "wrMaskEnable"   -> Ram_1w_1rs_bist.this.wrMaskEnable,
    "rdAddressWidth" -> Ram_1w_1rs_bist.this.rdAddressWidth,
    "rdDataWidth"    -> Ram_1w_1rs_bist.this.rdDataWidth
  )


  val io = new Bundle {
    val wr = new Bundle {
      val clk  = in Bool()
      val en   = in Bool()
      val mask = in Bits(wrMaskWidth bits)
      val addr = in UInt(wrAddressWidth bit)
      val data = in Bits(wrDataWidth bit)
    }

    val rd = new Bundle {
      val clk  = in Bool()
      val en   = in Bool()
      val addr = in  UInt(rdAddressWidth bit)
      val data = out Bits(rdDataWidth bit)
    }

    val bist = slave(BistBundle(wrMaskWidth, wrAddressWidth, wrDataWidth, rdAddressWidth, rdDataWidth))
  }

  mapClockDomain(wrClock,io.wr.clk)
  mapClockDomain(rdClock,io.rd.clk)
  noIoPrefix()
}

class MemBlackboxOf(val mem : Mem[Data]) extends SpinalTag
class PhaseMemBlackBoxingDefault(policy: MemBlackboxingPolicy = blackboxAll) extends PhaseMemBlackBoxingWithPolicy(policy){
  def doBlackboxing(topo: MemTopology): String = {
    val mem = topo.mem
    def wrapBool(that: Expression): Bool = that match {
      case that: Bool => that
      case that       =>
        val ret = Bool()
        ret.assignFrom(that)
        ret
    }

    def wrapConsumers(oldSource: Expression, newSource: Expression): Unit ={
      super.wrapConsumers(topo, oldSource, newSource)
    }

    def removeMem(): Unit ={
      super.removeMem(mem)
    }

    if (mem.initialContent != null) {
      return "Can't blackbox ROM"  //TODO
      //      } else if (topo.writes.size == 1 && topo.readsAsync.size == 1 && topo.portCount == 2) {
    } else if (topo.writes.size == 1 && (topo.readsAsync.nonEmpty || topo.readsSync.nonEmpty) && topo.writeReadSameAddressSync.isEmpty && topo.readWriteSync.isEmpty) {
      mem.component.rework {
        val wr = topo.writes(0)
        for (rd <- topo.readsAsync) {
          val clockDomain = wr.clockDomain
          val ctx = ClockDomainStack.set(clockDomain)

          val ram = new Ram_1w_1ra(
            wordWidth = mem.getWidth,
            wordCount = mem.wordCount,
            wrAddressWidth = wr.getAddressWidth,
            wrDataWidth = wr.data.getWidth,
            rdAddressWidth = rd.getAddressWidth,
            rdDataWidth = rd.getWidth,
            wrMaskWidth = if (wr.mask != null) wr.mask.getWidth else 1,
            wrMaskEnable = wr.mask != null,
            readUnderWrite = rd.readUnderWrite,
            technology = mem.technology
          )

          ram.addTag(new MemBlackboxOf(topo.mem.asInstanceOf[Mem[Data]]))

          ram.io.wr.en := wrapBool(wr.writeEnable) && clockDomain.isClockEnableActive
          ram.io.wr.addr.assignFrom(wr.address)
          ram.io.wr.data.assignFrom(wr.data)

          if (wr.mask != null)
            ram.io.wr.mask.assignFrom(wr.mask)
          else
            ram.io.wr.mask := B"1"

          ram.io.rd.addr.assignFrom(rd.address)
          wrapConsumers(rd, ram.io.rd.data)

          ram.setName(mem.getName())
          ctx.restore()
        }

        for (rd <- topo.readsSync) {
          //use blackbox that with bist port
          val ram = new Ram_1w_1rs_bist(
            wordWidth = mem.getWidth,
            wordCount = mem.wordCount,
            wrClock = wr.clockDomain,
            rdClock = rd.clockDomain,
            wrAddressWidth = wr.getAddressWidth,
            wrDataWidth = wr.data.getWidth,
            rdAddressWidth = rd.getAddressWidth,
            rdDataWidth = rd.getWidth,
            wrMaskWidth = if (wr.mask != null) wr.mask.getWidth else 1,
            wrMaskEnable = wr.mask != null,
            readUnderWrite = rd.readUnderWrite,
            technology = mem.technology
          )
          ram.addTag(new MemBlackboxOf(topo.mem.asInstanceOf[Mem[Data]]))

          ram.io.wr.en := wrapBool(wr.writeEnable) && wr.clockDomain.isClockEnableActive
          ram.io.wr.addr.assignFrom(wr.address)
          ram.io.wr.data.assignFrom(wr.data)

          if (wr.mask != null)
            ram.io.wr.mask.assignFrom(wr.mask)
          else
            ram.io.wr.mask := B"1"

          ram.io.rd.en := wrapBool(rd.readEnable) && rd.clockDomain.isClockEnableActive
          ram.io.rd.addr.assignFrom(rd.address)
          wrapConsumers(rd, ram.io.rd.data)

          //find the bistctrl module
          val toplevel = mem.component.parents().headOption.getOrElse(mem.component)
          val bistctrl = toplevel.children.find(c => c.isInstanceOf[BistCtrl])
          bistctrl match {
            case Some(bistctrl) => {
              val connect = (bundle: BistBundle, memcomponent: Component) => {
                memcomponent.rework {
                  ram.io.bist.wr_clk  := bundle.wr_clk.pull()
                  ram.io.bist.wr_en   := bundle.wr_en.pull()
                  ram.io.bist.wr_mask := bundle.wr_mask.pull()
                  ram.io.bist.wr_addr := bundle.wr_addr.pull()
                  ram.io.bist.wr_data := bundle.wr_data.pull()
                  ram.io.bist.rd_addr := bundle.rd_addr.pull()
                  ram.io.bist.rd_clk  := bundle.rd_clk.pull()
                  ram.io.bist.rd_en   := bundle.rd_en.pull()
                }
                toplevel.rework {
                  bundle.rd_data := ram.io.bist.rd_data.pull()
                }
              }
              bistctrl.asInstanceOf[BistCtrl].mems = (
                mem.wordCount,
                mem.getWidth,
                cloneOf(ram.io.bist),
                mem.component,
                connect(_, _)
              ) :: bistctrl.asInstanceOf[BistCtrl].mems
            }
            case None => return "bist ctrl module not found"
          }

          ram.setName(mem.getName())
        }

        removeMem()
      }
    } else if (topo.portCount == 1 && topo.readWriteSync.size == 1) {

      mem.component.rework {
        val port = topo.readWriteSync.head

        val ram = port.clockDomain on new Ram_1wrs(
          wordWidth = port.width,
          wordCount = mem.wordCount*mem.width/port.width,
          technology = mem.technology,
          readUnderWrite = port.readUnderWrite,
          duringWrite = port.duringWrite,
          maskWidth = if (port.mask != null) port.mask.getWidth else 1,
          maskEnable = port.mask != null
        )

        ram.addTag(new MemBlackboxOf(topo.mem.asInstanceOf[Mem[Data]]))

        ram.io.addr.assignFrom(port.address)
        ram.io.en.assignFrom(wrapBool(port.chipSelect) && port.clockDomain.isClockEnableActive)
        ram.io.wr.assignFrom(port.writeEnable)
        ram.io.wrData.assignFrom(port.data)

        if (port.mask != null)
          ram.io.mask.assignFrom(port.mask)
        else
          ram.io.mask := B"1"

        wrapConsumers(port, ram.io.rdData)

        ram.setName(mem.getName())
        removeMem()
      }
    } else if (topo.portCount == 2 && topo.readWriteSync.size == 2) {

      mem.component.rework {
        val portA = topo.readWriteSync(0)
        val portB = topo.readWriteSync(1)

        val ram = new Ram_2wrs(
          wordWidth = mem.getWidth,
          wordCount = mem.wordCount,
          technology = mem.technology,
          portA_readUnderWrite = portA.readUnderWrite,
          portA_duringWrite = portA.duringWrite,
          portA_clock = portA.clockDomain,
          portA_addressWidth = portA.getAddressWidth,
          portA_dataWidth = portA.getWidth,
          portA_maskWidth = if (portA.mask != null) portA.mask.getWidth else 1,
          portA_maskEnable = portA.mask != null,
          portB_readUnderWrite = portB.readUnderWrite,
          portB_duringWrite = portB.duringWrite,
          portB_clock = portB.clockDomain,
          portB_addressWidth = portB.getAddressWidth,
          portB_dataWidth = portB.getWidth,
          portB_maskWidth = if (portB.mask != null) portB.mask.getWidth else 1,
          portB_maskEnable = portB.mask != null
        )

        ram.addTag(new MemBlackboxOf(topo.mem.asInstanceOf[Mem[Data]]))

        ram.io.portA.addr.assignFrom(portA.address)
        ram.io.portA.en.assignFrom(wrapBool(portA.chipSelect) && portA.clockDomain.isClockEnableActive)
        ram.io.portA.wr.assignFrom(portA.writeEnable)
        ram.io.portA.wrData.assignFrom(portA.data)
        ram.io.portA.mask.assignFrom((if (portA.mask != null) portA.mask else B"1"))
        wrapConsumers(portA, ram.io.portA.rdData)

        ram.io.portB.addr.assignFrom(portB.address)
        ram.io.portB.en.assignFrom(wrapBool(portB.chipSelect) && portB.clockDomain.isClockEnableActive)
        ram.io.portB.wr.assignFrom(portB.writeEnable)
        ram.io.portB.wrData.assignFrom(portB.data)
        ram.io.portB.mask.assignFrom((if (portB.mask != null) portB.mask else B"1"))
        wrapConsumers(portB, ram.io.portB.rdData)

        ram.setName(mem.getName())
        removeMem()
      }
    } else {
      return "Unblackboxable memory topology" //TODO
    }
    return null
  }
}

class GenBistCtrl extends PhaseNetlist {

  override def impl(pc: PhaseContext): Unit = {
    val bistctrl = pc.topLevel.children.find(c => c.isInstanceOf[BistCtrl])
    bistctrl match {
      case Some(bistctrl) => {
        bistctrl.rework {
          bistctrl.asInstanceOf[BistCtrl].tasks
        }
      }
      case None =>
    }
  }
  
}
