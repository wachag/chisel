/*
 Copyright (c) 2011, 2012, 2013 The Regents of the University of
 California (Regents). All Rights Reserved.  Redistribution and use in
 source and binary forms, with or without modification, are permitted
 provided that the following conditions are met:

    * Redistributions of source code must retain the above
      copyright notice, this list of conditions and the following
      two paragraphs of disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      two paragraphs of disclaimer in the documentation and/or other materials
      provided with the distribution.
    * Neither the name of the Regents nor the names of its contributors
      may be used to endorse or promote products derived from this
      software without specific prior written permission.

 IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 REGENTS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
 LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 A PARTICULAR PURPOSE. THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF
 ANY, PROVIDED HEREUNDER IS PROVIDED "AS IS". REGENTS HAS NO OBLIGATION
 TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 MODIFICATIONS.
*/

package Chisel
import Node._
import Reg._
import ChiselError._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{Queue=>ScalaQueue}
import scala.collection.mutable.Stack
import scala.collection.mutable.{HashSet, HashMap, LinkedHashMap}
import java.lang.reflect.Modifier._
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream

object Backend {
  var moduleNamePrefix = ""
}

trait FileSystemUtilities {
  /** Ensures a directory *dir* exists on the filesystem. */
  def ensureDir(dir: String): String = {
    val d = dir + (if (dir == "" || dir(dir.length-1) == '/') "" else "/")
    new File(d).mkdirs()
    d
  }

  def createOutputFile(name: String): java.io.FileWriter = {
    val baseDir = ensureDir(Driver.targetDir)
    new java.io.FileWriter(baseDir + name)
  }
}

abstract class Backend extends FileSystemUtilities{
  /* Set of keywords which cannot be used as node and component names. */
  val keywords: HashSet[String];
  val nameSpace = HashSet[String]() 
  /* Set of Ops that this backend doesn't natively support and thus must be
     lowered to simpler Ops. */
  val needsLowering = Set[String]()

  /* Whether or not this backend decomposes along Module boundaries. */
  def isEmittingComponents: Boolean = false

  def depthString(depth: Int): String = {
    var res = "";
    for (i <- 0 until depth)
      res += "  ";
    res
  }

  def extractClassName(comp: Module): String = {
    val cname  = comp.getClass().getName().replace("$", "_")
    val dotPos = cname.lastIndexOf('.');
    Backend.moduleNamePrefix + (
      if (dotPos >= 0) cname.substring(dotPos + 1) else cname);
  }

  protected def genIndent(x: Int): String = {
    if(x == 0) "" else "    " + genIndent(x-1);
  }

  /* Returns a string derived from _name_ that can be used as a valid
   identifier for the targeted backend. */
  def asValidName( name: String ): String = {
    if (keywords.contains(name)) name + "_" else name;
  }

  def nameAll {
    // module naming
    val byNames = LinkedHashMap[String, ArrayBuffer[Module]]();
    for (c <- Driver.sortedComps) {
      if( c.name.isEmpty ) {
        /* We don't have a name because we are not dealing with
         a class member. */
        val className = extractClassName(c);
        if( byNames contains className ) {
          byNames(className) += c
        } else {
          byNames(className) = ArrayBuffer[Module](c)
        }
      }
    }

    for( (className, comps) <- byNames ) {
      if( comps.length > 1 ) {
        for( (c, index) <- comps.zipWithIndex ) {
          c.name = className + "_" + index.toString
        }
      } else {
        comps(0).name = className;
      }
    }

    // binding naming
    for (comp <- Driver.sortedComps) {
      for (bind <- comp.bindings) {
        var genName = if (bind.targetNode.name == null || bind.targetNode.name.length() == 0) "" 
                      else bind.targetComponent.name + "_" + bind.targetNode.name
        if(nameSpace contains genName) genName += ("_" + bind.emitIndex);
        bind.name = asValidName(genName); // Not using nameIt to avoid override
        bind.named = true;
      }
    }
  }

  def fullyQualifiedName( m: Node ): String = {
    m match {
      case l: Literal => l.toString;
      case any       =>
        if (m.name != ""
          && m != Driver.topComponent.defaultResetPin && m.component != null) {
          /* Only modify name if it is not the reset signal
           or not in top component */
          if(m.name != "reset" && m.component != Driver.topComponent) {
            m.component.getPathName + "__" + m.name;
          } else {
            m.name
          }
        } else {
          m.name
        }
    }
  }

  def emitTmp(node: Node): String =
    emitRef(node)

  def emitRef(node: Node): String = {
    node match {
      case r: Reg =>
        if (r.name == "") "R" + r.emitIndex else r.name
      case _ =>
        if(node.name == "") {
          "T" + node.emitIndex
        } else {
          node.name
        }
    }
  }

  def emitRef(c: Module): String = c.name
  def emitDec(node: Node): String = ""

  val preElaborateTransforms = ArrayBuffer[(Module) => Unit]()
  val transforms = ArrayBuffer[(Module) => Unit]()
  if (Driver.isCSE) transforms += CSE.transform
  val analyses = ArrayBuffer[(Module) => Unit]()

  def bfsModule(visit: Module => Unit) = {
    val walked = HashSet[Module]()
    val queue = ScalaQueue[Module](Driver.topComponent)

    def isVisiting(module: Module) = 
      !(module == null) && !(walked contains module)

    while(!queue.isEmpty) {
      val top = queue.dequeue
      walked += top
      visit(top)
      for (child <- top.children) {
        if (isVisiting(child)) {
          queue enqueue child
          walked += child
        }
      }
    }
  }

  def initializeDFS: Stack[Node] = {
    val res = new Stack[Node]

    /* XXX Make sure roots are consistent between initializeBFS, initializeDFS
     and findRoots.
     */
    for( c <- Driver.components ) {
      for( a <- c.debugs ) {
        res.push(a)
      }
      for((n, flat) <- c.io.flatten) {
        res.push(flat)
      }
    }
    res
  }

  /** Nodes which are created outside the execution trace from the toplevel
    component constructor (i.e. through the () => Module(new Top()) ChiselMain
    argument) will have a component field set to null. For example, genMuxes,
    forceMatchWidths and transforms (all called from Backend.elaborate) create
    such nodes.

    This method walks all nodes from all component roots (outputs, debugs).
    and reassociates the component to the node both ways (i.e. in Driver.nodes
    and Node.component).

    We assume here that all nodes at the components boundaries (io) have
    a non-null and correct node/component association. We further assume
    that nodes generated in elaborate are inputs to a node whose component
    field is set.

    Implementation Node:
    At first we did implement *collectNodesIntoComp* to handle a single
    component at a time but that did not catch the cases where Regs are
    passed as input to sub-module without being tied to an output
    of *this.component*.
    */
  def isBitsIo(node: Node, dir: IODirection): Boolean = node match {
    case b: Bits => b.isIo && b.dir == dir
    case _ => false
  }

  def collectNodesIntoComp(dfsStack: Stack[Node]) {
    val walked = new HashSet[Node]()
    walked ++= dfsStack
    // invariant is everything in the stack is walked and has a non-null component
    while(!dfsStack.isEmpty) {
      val node = dfsStack.pop
      /*
      we're tracing from outputs -> inputs, so if node is an input, then its
      inputs belong to the outside component. Otherwise, its inputs are the same
      as node's inputs.
      */
      val curComp = 
        if ( node.isIo && node.asInstanceOf[Bits].dir == INPUT ) {
          node.component.parent
        } else {
          node.component
        }
      if (node.component == null) {
        println("NULL NODE COMPONENT " + node)
      }
      if (!node.component.nodes.contains(node))
        node.component.nodes += node
      for (input <- node.inputs) {
        if (input.component != null && input.component != node.component) {
          if (!input.isLit &&
              !isBitsIo(input, OUTPUT) && !isBitsIo(node, INPUT) &&
              // ok if parent referring to any child nodes
              // not symmetric and only applies to direct children
              // READ BACK INPUT -- TODO: TIGHTEN THIS UP
              !isBitsIo(input, INPUT))
            ChiselErrors += new ChiselError(() => { "Illegal cross module reference between " + node + " and " + input }, node.line)
        }
        if(!walked.contains(input)) {
          if( input.component == null ) {
            input.component = curComp
          }
          walked += input
          dfsStack.push(input)
        }
      }
    }

    assert(dfsStack.isEmpty)
  }

  def execute(c: Module, walks: ArrayBuffer[(Module) => Unit]): Unit = {
    for (w <- walks)
      w(c)
  }

  def pruneUnconnectedIOs(m: Module) {
    val inputs = m.io.flatten.filter(_._2.dir == INPUT)
    val outputs = m.io.flatten.filter(_._2.dir == OUTPUT)

    for ((name, i) <- inputs) {
      if (i.inputs.length == 0 && m != Driver.topComponent)
        if (i.consumers.length > 0) {
          if (Driver.warnInputs)
            ChiselError.warning({"UNCONNECTED INPUT " + emitRef(i) + " in COMPONENT " + i.component +
                                 " has consumers"})
          i.driveRand = true
        } else {
          if (Driver.warnInputs)
            ChiselError.warning({"FLOATING INPUT " + emitRef(i) + " in COMPONENT " + i.component})
          i.prune = true
        }
    }

    for ((name, o) <- outputs) {
      if (o.inputs.length == 0 && !o.component.isInstanceOf[BlackBox]) {
        if (o.consumers.length > 0) {
          if (Driver.warnOutputs)
            ChiselError.warning({"UNCONNECTED OUTPUT " + emitRef(o) + " in component " + o.component + 
                                 " has consumers on line " + o.consumers(0).line})
          o.driveRand = true
        } else {
          if (Driver.warnOutputs)
            ChiselError.warning({"FLOATING OUTPUT " + emitRef(o) + " in component " + o.component})
          o.prune = true
        }
      }
    }
  }

  def pruneNodes {
    val walked = new HashSet[Node]
    val bfsQueue = new ScalaQueue[Node]
    for (node <- Driver.randInitIOs) bfsQueue.enqueue(node)
    var pruneCount = 0

    // conduct bfs to find all reachable nodes
    while(!bfsQueue.isEmpty){
      val top = bfsQueue.dequeue
      walked += top
      val prune = top.inputs.map(_.prune).foldLeft(true)(_ && _)
      pruneCount+= (if (prune) 1 else 0)
      top.prune = prune
      for(i <- top.consumers) {
        if(!(i == null)) {
          if(!walked.contains(i)) {
            bfsQueue.enqueue(i)
            walked += i
          }
        }
      }
    }
    ChiselError.warning("Pruned " + pruneCount + " nodes due to unconnected inputs")
  }

  def emitDef(node: Node): String = ""

  def levelChildren(root: Module) {
    root.level = 0;
    root.traversal = VerilogBackend.traversalIndex;
    VerilogBackend.traversalIndex = VerilogBackend.traversalIndex + 1;
    for(child <- root.children) {
      levelChildren(child)
      root.level = math.max(root.level, child.level + 1);
    }
  }

  def gatherChildren(root: Module): ArrayBuffer[Module] = {
    var result = new ArrayBuffer[Module]();
    for (child <- root.children)
      result = result ++ gatherChildren(child);
    result ++ ArrayBuffer[Module](root);
  }

  // go through every Module and set its clock and reset field
  def assignClockAndResetToModules {
    for (module <- Driver.sortedComps.reverse) {
      if (module.clock == null)
        module.clock = module.parent.clock
      if (!module.hasExplicitReset)
        module.reset_=
    }
  }

  // go through every Module, add all clocks+resets used in it's tree to it's list of clocks+resets
  def gatherClocksAndResets {
    for (parent <- Driver.sortedComps) {
      for (child <- parent.children) {
        for (clock <- child.clocks) {
          parent.addClock(clock)
        }
        for (reset <- child.resets.keys) {
          // create a reset pin in parent if reset does not originate in parent and 
          // if reset is not an output from one of parent's children
          if (reset.component != parent && !parent.children.contains(reset.component))
            parent.addResetPin(reset)

          // special case for implicit reset
          if (reset == Driver.implicitReset && parent == Driver.topComponent)
            if (!parent.resets.contains(reset))
              parent.resets += (reset -> reset)
        }
      }
    }
  }

  def connectResets {
    for (parent <- Driver.sortedComps) {
      for (child <- parent.children) {
        for (reset <- child.resets.keys) {
          if (child.resets(reset).inputs.length == 0)
            if (parent.resets.contains(reset))
              child.resets(reset).inputs += parent.resets(reset)
            else 
              child.resets(reset).inputs += reset
        }
      }
    }
  }

  def nameRsts {
    for (comp <- Driver.sortedComps) {
      for (rst <- comp.resets.keys) {
        if (!comp.resets(rst).named)
            comp.resets(rst).setName(rst.name)
      }
    }
  }

  // walk forward from root register assigning consumer clk = root.clock
  private def createClkDomain(root: Node, walked: HashSet[Node]) = {
    val dfsStack = new Stack[Node]
    walked += root; dfsStack.push(root)
    val clock = root.clock
    while(!dfsStack.isEmpty) {
      val node = dfsStack.pop
      for (consumer <- node.consumers) {
        if (!consumer.isInstanceOf[Delay] && !walked.contains(consumer)) {
          val c1 = consumer.clock
          val c2 = clock
          if(!(consumer.clock == null || consumer.clock == clock)) {
            ChiselError.warning({consumer.getClass + " " + emitRef(consumer) + " " + emitDef(consumer) + "in module" +
                                 consumer.component + " resolves to clock domain " + 
                                 emitRef(c1) + " and " + emitRef(c2) + " traced from " + root.name})
          } else { consumer.clock = clock }
          walked += consumer
          dfsStack.push(consumer)
        }
      }
    }
  }

  def elaborate(c: Module): Unit = {
    Driver.setTopComponent(c)
    Driver.sortComponents

    /* XXX If we call nameAll here and again further down, we end-up with
     duplicate names in the generated C++.
    nameAll(c) */

    ChiselError.info("elaborating modules")
    Driver.components.foreach(_.elaborate(0))

    /* XXX We should name all signals before error messages are generated
     so as to give a clue where problems are showing up but that interfers
     with the *bindings* (see later comment). */
    for (c <- Driver.components)
      c.markComponent();
    // XXX This will create nodes after the tree is traversed!
    c.genAllMuxes;
    ChiselError.checkpoint()
    execute(c, preElaborateTransforms)
    Driver.components.foreach(_.postMarkNet(0))
    ChiselError.info("// COMPILING " + c + "(" + c.children.length + ")");

    assignClockAndResetToModules
    Driver.sortedComps.map(_.addDefaultReset)
    c.addClockAndReset
    gatherClocksAndResets
    connectResets

    ChiselError.info("inferring widths")
    c.inferAll
    ChiselError.info("checking widths")
    c.forceMatchingWidths
    ChiselError.info("lowering complex nodes to primitives")
    c.lowerNodes(needsLowering)
    ChiselError.info("removing type nodes")
    val nbNodes = c.removeTypeNodes()
    ChiselError.checkpoint()

    /* *collectNodesIntoComp* associates components to nodes that were
     created after the call tree has been executed (ie. in genMuxes
     and forceMatchWidths).

     The purpose of *collectNodesIntoComp* is to insure user-defined
     transforms will be able to query a component for all its nodes
     and a node for its component.

     Technically all user-defined transforms are responsible to update
     nodes and component correctly or call collectNodesIntoComp on return.
     */
    ChiselError.info("resolving nodes to the components")
    collectNodesIntoComp(initializeDFS)

    // two transforms added in Mem.scala (referenced and computePorts)
    ChiselError.info("executing custom transforms")
    execute(c, transforms)

    Driver.sortedComps.map(_.nodes.map(_.addConsumers))
    c.traceNodes();
    val clkDomainWalkedNodes = new HashSet[Node]
    for (comp <- Driver.sortedComps)
      for (node <- comp.nodes)
        if (node.isInstanceOf[Reg])
            createClkDomain(node, clkDomainWalkedNodes)
    ChiselError.checkpoint()

    /* We execute nameAll after traceNodes because bindings would not have been
       created yet otherwise. */
    nameAll
    nameRsts

    execute(c, analyses)

    for (comp <- Driver.sortedComps ) {
      // remove unconnected outputs
      pruneUnconnectedIOs(comp)
    }

    ChiselError.checkpoint()

    if(!Driver.dontFindCombLoop) {
      ChiselError.info("checking for combinational loops")
      c.findCombLoop();
      ChiselError.checkpoint()
      ChiselError.info("NO COMBINATIONAL LOOP FOUND")
    }
    if (Driver.saveComponentTrace) {
      printStack
    }
  }

  def compile(c: Module, flags: String = null): Unit = { }

  def checkPorts(topC: Module) {

    def prettyPrint(n: Node, c: Module) {
      val dir = if (n.asInstanceOf[Bits].dir == INPUT) "Input" else "Output"
      val portName = n.name
      val compName = c.name
      val compInstName = c.moduleName
      ChiselError.warning(dir + " port " + portName
        + " is unconnected in module " + compInstName + " " + compName)
    }

    for (c <- Driver.components) {
      if (c != topC) {
        for ((n,i) <- c.io.flatten) {
          if (i.inputs.length == 0) {
            prettyPrint(i, c)
          }
        }
      }
    }

  }

  /** Prints the call stack of Component as seen by the push/pop runtime. */
  protected def printStack {
    var res = ""
    for((i, c) <- Driver.printStackStruct){
      res += (genIndent(i) + c.moduleName + " " + c.name + "\n")
    }
    ChiselError.info(res)
  }

}


