// Get Polylux from the official package repository
#import "@preview/polylux:0.3.1": *
#import "@preview/codelst:2.0.0": sourcecode
#import "@preview/diagraph:0.2.0": *

// Make the paper dimensions fit for a presentation and the text larger
#set page(paper: "presentation-16-9")
#set text(size: 25pt)

// Use #polylux-slide to create a slide and style it using your favourite Typst functions
#polylux-slide[
  #align(horizon + center)[
    = Memory Black Box Tips

    #link("https://github.com/yportne13")[yportne13]

    Dec 10, 2023

    #link("https://github.com/yportne13/SpinalMemBist")[https://github.com/yportne13/SpinalMemBist]
  ]
]

#show link: underline

#polylux-slide[
  == Tools

  typst: #link("https://github.com/typst/typst")

  polylux: #link("https://github.com/andreasKroepelin/polylux")

  StyleTTS2: #link("https://github.com/yl4579/StyleTTS2")

  yi-34b-chat: #link("https://huggingface.co/01-ai/Yi-34B-Chat")
]

#polylux-slide[
  #set text(size: 25pt)
  == Memory
  #set text(size: 18pt)

  what we get:

  #sourcecode[```verilog
  reg [7:0] mems_0 [0:11];
  always @(posedge clk) begin
    if(io_we) begin
      mems_0[io_waddr] <= io_win;
    end
  end
  always @(posedge clk) begin
    if(io_ren) begin
      io_rout <= mems_0[io_raddr];
    end
  end
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Memory
  #set text(size: 18pt)

  what we need:

  #sourcecode[```verilog
  Ram_1w_1rs #(
    .wordCount(12),
    .wordWidth(8),
    ...
  ) mems_0 (
    .wr_clk  (clk                ), //i
    .wr_en   (io_we              ), //i
    .wr_addr (io_waddr[3:0]      ), //i
    .wr_data (io_win[7:0]        ), //i
    .rd_clk  (clk                ), //i
    .rd_en   (io_ren             ), //i
    .rd_addr (io_raddr[3:0]      ), //i
    .rd_data (io_rout[7:0]       )  //o
  );
```]
]

#let bright_blue = "#55ccff"
#let bright_green = "#55ffcc"
#let viz_box(color) = "node[shape=record, style=filled, fillcolor=\"replacecolor\", width = 2, height = 0.8]".replace("replacecolor", color)

#let myrender(idx) = {
  let text = ```
  digraph {
    rankdir=LR
    set-color
    splithere
    struct1 [label = "declare
black box"]
    splithere
    struct2 [label = "foreach"]
    splithere
    struct3 [label = "phase"]
    splithere
    struct4 [label = "use"]
    splithere
    struct1 -> struct2 -> struct3 -> struct4
  }
```.text.replace("set-color", viz_box(bright_blue))
  let array = text.split("splithere")
  array.insert(idx+1, viz_box(bright_green))
  array.insert(idx+3, viz_box(bright_blue))
  render(array.join())
}

#polylux-slide[
  #set text(size: 25pt)
  == SpinalHDL Origin Example
  #set text(size: 18pt)

  #myrender(0)

  #link("https://github.com/SpinalHDL/SpinalHDL/blob/v1.9.4/core/src/main/scala/spinal/core/internals/Phase.scala#L876")[Phase.scala]
  #sourcecode[```scala
class Ram_1w_1rs(
  val wordWidth      : Int,
  ...
) extends BlackBox {
  val io = new Bundle {
    ...
  }
  ...
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == SpinalHDL Origin Example
  #set text(size: 18pt)

  #myrender(1)

  #link("https://github.com/SpinalHDL/SpinalHDL/blob/v1.9.4/core/src/main/scala/spinal/core/internals/Phase.scala#L876")[Phase.scala]
  #sourcecode[```scala
walkBaseNodes{
  case mem: Mem[_] => mems += mem
  case ec: ExpressionContainer =>
    ec.foreachExpression{
      case port: MemPortStatement => ...
      case _ =>
    }
  case _ =>
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == SpinalHDL Origin Example
  #set text(size: 18pt)

  #myrender(2)

  #link("https://github.com/SpinalHDL/SpinalHDL/blob/v1.9.4/core/src/main/scala/spinal/core/internals/Phase.scala#L876")[Phase.scala]
  #sourcecode(
    numbers-start: 876
  )[```scala
class PhaseMemBlackBoxingDefault(policy: MemBlackboxingPolicy) extends PhaseMemBlackBoxingWithPolicy(policy){
  ...
  mem.component.rework {
    val ram = new Ram_1w_1rs(...)
    ...
    removeMem()
  }
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == SpinalHDL origin example
  #set text(size: 18pt)

  #myrender(3)

  use memory as normal

  #sourcecode[```scala
  val mem = Mem(Bits(8 bits), 64)
  mem.write(io.waddr, io.win, io.we)
  mem.readSync(io.raddr)
```]

  #sourcecode(
    highlighted: (2,)
  )[```scala
SpinalConfig(
  memBlackBoxers = ArrayBuffer(new PhaseMemBlackBoxingDefault())
).generateVerilog(new toplevel)
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Implementations based on size
  #set text(size: 18pt)

  if size is small, use reg

  #link("https://github.com/yportne13/SpinalMemBist/blob/master/src/scala/main/ToTopIo/MemLib.scala#L161")[Memlib.scala]
  #sourcecode(
    highlighted: (166, 167,),
    numbers-start: 161
  )[```scala
def removeMem(): Unit ={
  super.removeMem(mem)
}

// use blackbox mem or not
val useBlack = mem.getWidth * mem.wordCount > 10*8
if(useBlack)
if (mem.initialContent != null) {
  return "Can't blackbox ROM"  //TODO
```]
  #sourcecode[```verilog
  reg [7:0] mems_0 [0:11];
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Implementations based on size
  #set text(size: 18pt)

  if size is big, divide into multi mems
  #sourcecode(
    numbers-start: 67
  )[```scala
class Multi_Ram_Wrapper(...) extends Component {
  ...
  this.parent.rework {
    io.wr.clk := wrClock.readClockWire
    io.rd.clk := rdClock.readClockWire
  }

  val sepBy = 32
  val memNum = 1 << log2Up(wordCount/sepBy)
  require(wordCount/memNum*memNum == wordCount)
  val mems = (0 until memNum).map(idx => {
    val mem = new Ram_1w_1rs(...)
    ...
  })
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Connect to Top
  #set text(size: 18pt)

  #link("https://github.com/yportne13/SpinalMemBist/blob/master/src/scala/main/ToTopIo/MemLib.scala#L59")[Memlib.scala line 59]
  #sourcecode(
    highlighted: (59,),
    numbers-start: 58
  )[```scala
class Ram_1w_1rs_bist() extends BlackBox {
  val bist_en = in Bool()
  ...
}
  ```]

  #link("https://github.com/yportne13/SpinalMemBist/blob/master/src/scala/main/ToTopIo/MemLib.scala#L268")[Memlib.scala line 268]
  #sourcecode(
    numbers-start: 266
  )[```scala
mem.component.rework {
  ...
  val toplevel = mem.component.parents().headOption.getOrElse(mem.component)
  ram.io.bist_en := toplevel.asInstanceOf[Component{
    val io: Bundle{val bist_en: Bool}}].io.bist_en.pull()
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Extend Signal
  #set text(size: 18pt)

  Target:
  #sourcecode(
  )[```scala
  val mem = Mem(Bits(8 bits), 64)
  mem.write(io.waddr, io.win, io.we)
  mem.readSync(io.raddr)
  mem.bist(io.bist_en)
```]

  #sourcecode()[```scala
package object MemLib {
  implicit class MemBistEnPort[T <: Data](mem: Mem[T]) {
    def bistEn(that: Bool) {
      that.addTag(MemBistEn(mem))
    }
  }
}
  ```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Extend Signal
  #set text(size: 18pt)

  #sourcecode(
    numbers-start: 168
  )[```scala
mem.component.dslBody.walkStatements{
  case s: Bool => {
    s.getTags().foreach{
      case MemBistEn(m) => {
        if(mem == m){
          ram.io.bist_en := s
        }
      }
      case _ =>
    }
  }
  case _ =>
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Ctrl Module
  #set text(size: 18pt)

  #link("https://github.com/yportne13/SpinalMemBist/blob/master/src/scala/main/example.scala#L8")[example.scala line 8]
  #sourcecode(
    numbers-start: 8
  )[```scala
class Bist extends BistCtrl {
  val io = new Bundle {
    val en = in Bool()
  }

  override def tasks: Unit = {
    mems.groupBy(x => (x._1, x._2))
      .foreach{case ((cnt, width), mems) =>
        val wr_addr = Counter(cnt, io.en)
        mems.foreach{case (count, width, bundle, memcomponent, task) =>
          this.rework {...}
        }
      }
  }
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Ctrl Module
  #set text(size: 18pt)

  #link("https://github.com/yportne13/SpinalMemBist/blob/master/src/scala/main/MemBist.scala#L199")[MemBist.scala line 199]
  #sourcecode(
    numbers-start: 199
  )[```scala
val toplevel = mem.component.parents().headOption.getOrElse(mem.component)
val bistctrl = toplevel.children.find(c => c.isInstanceOf[BistCtrl])
bistctrl match {
  case Some(bistctrl) => {
    val connect = (bundle: BistBundle, memcomponent: Component) => {
      ... all the connection logics
    }
    bistctrl.asInstanceOf[BistCtrl].mems = (
      mem.wordCount, mem.getWidth, cloneOf(ram.io.bist), mem.component,
      connect(_, _)
    ) :: bistctrl.asInstanceOf[BistCtrl].mems
  }
  case None => return "bist ctrl module not found"
}
```]
]

#polylux-slide[
  #set text(size: 25pt)
  == Resize Lint
  #set text(size: 18pt)

  #link("https://github.com/yportne13/SpinalMemBist/blob/master/src/scala/main/resize.scala#L13")[resize.scala line 13]
  #sourcecode(
    numbers-start: 13
  )[```scala
pc.topLevel.walkComponents(c => {
  c.dslBody.foreachStatements{
    case as: AssignmentStatement => {
      as.source match {
        case s: BaseType => {
          if(s.hasTag(tagAutoResize))
          as.target match {
            case t: BaseType => {
              if(t.getBitsWidth < s.getBitsWidth)
                PendingError(s"INVALID RESIZE (${t.getBitsWidth} bits <- ${s.getBitsWidth} bits) on ${as.toStringMultiLine} at \n${as.getScalaLocationLong}")
            }
```]
]

#polylux-slide[
  #align(horizon + center)[
    = Thank You

    QA: #link("https://github.com/yportne13/SpinalMemBist")[https://github.com/yportne13/SpinalMemBist]
  ]
]
