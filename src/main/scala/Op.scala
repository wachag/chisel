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
import scala.math.max
import Node._
import Literal._
import Op._

object chiselCast {
  def apply[S <: Node, T <: Bits](x: S)(gen: => T): T = {
    val res = gen
    res assign x.toNode
    res
  }
}

object UnaryOp {
  val Op = OpGen1({ new UnaryOp(_: String) }) _
  def apply(op: String, widthInfer: (=> Node) => Width, x: Node): Node = {
    Op(op, widthInfer, x)
  }
  def apply(x: Node, op: String): Node = {
    op match {
      case "~" => Op("~", widthOf(0), x)
      case "f-" => Op("f-", fixWidth(32), x)
      case "fsin" => Op("fsin", fixWidth(32), x)
      case "fcos" => Op("fcos", fixWidth(32), x)
      case "ftan" => Op("ftan", fixWidth(32), x)
      case "fsqrt" => Op("fsqrt", fixWidth(32), x)
      case "flog" => Op("flog", fixWidth(32), x)
      case "ffloor" => Op("ffloor", fixWidth(32), x)
      case "fceil" => Op("fceil", fixWidth(32), x)
      case "fround" => Op("fround", fixWidth(32), x)
      case "d-" => Op("d-", fixWidth(64), x)
      case "dsin" => Op("dsin", fixWidth(64), x)
      case "dcos" => Op("dcos", fixWidth(64), x)
      case "dtan" => Op("dtan", fixWidth(64), x)
      case "dsqrt" => Op("dsqrt", fixWidth(64), x)
      case "dlog" => Op("dlog", fixWidth(64), x)
      case "dfloor" => Op("dfloor", fixWidth(64), x)
      case "dceil" => Op("dceil", fixWidth(64), x)
      case "dround" => Op("dround", fixWidth(64), x)
      case any => throw new Exception("Unrecognized operator " + op)
    }
  }
}

object BinaryOp {
  val Op = OpGen2({ new BinaryOp(_)}) _
  def apply(op: String, widthInfer: (=> Node) => Width, x: Node, y: Node): Node = {
    Op(op, widthInfer, x, y)
  }
  def apply(x: Node, y: Node, op: String): Node = {
    op match {
      case "<<"  => Op("<<", lshWidthOf(0, y),  x, y )
      case ">>"  => Op(">>", rshWidthOf(0, y),  x, y )
      case "s>>" => Op("s>>", rshWidthOf(0, y),  x, y )
      case "+"   => Op("+", maxWidth _,  x, y )
      case "*"   => Op("*", sumWidth _,  x, y )
      case "s*s" => Op("s*s", sumWidth _,  x, y )
      case "s*u" => Op("s*u", mulSUWidth _,  x, y )
      case "/"   => Op("/", widthOf(0),  x, y )
      case "s/s" => Op("s/s", widthOf(0),  x, y )
      case "%"   => Op("%", minWidth _,  x, y )
      case "s%s" => Op("s%s", minWidth _,  x, y )
      case "^"   => Op("^", maxWidth _,  x, y )
      case "-"   => Op("-", maxWidth _,  x, y )
      case "##"  => Op("##", sumWidth _,  x, y )
      case "&"   => Op("&", maxWidth _, x, y )
      case "|"   => Op("|", maxWidth _, x, y )
      case "f+"  => Op("f+", fixWidth(32), x, y )
      case "f-"  => Op("f-", fixWidth(32), x, y )
      case "f*"  => Op("f*", fixWidth(32), x, y )
      case "f/"  => Op("f/", fixWidth(32), x, y )
      case "f%"  => Op("f%", fixWidth(32), x, y )
      case "fpow"  => Op("fpow", fixWidth(32), x, y )
      case "d+"  => Op("d+", fixWidth(64), x, y )
      case "d-"  => Op("d-", fixWidth(64), x, y )
      case "d*"  => Op("d*", fixWidth(64), x, y )
      case "d/"  => Op("d/", fixWidth(64), x, y )
      case "d%"  => Op("d%", fixWidth(64), x, y )
      case "dpow"  => Op("dpow", fixWidth(64), x, y )
      case any   => throw new Exception("Unrecognized operator " + op)
    }
  }

  // width inference functions for signed-unsigned operations
  private def mulSUWidth(x: => Node) = sumWidth(x) - 1
  private def divUSWidth(x: => Node) = widthOf(0)(x) - 1
  private def modUSWidth(x: => Node) = x.inputs(1).needWidth().min(x.inputs(0).needWidth() - 1)
  private def modSUWidth(x: => Node) = x.inputs(0).needWidth().min(x.inputs(1).needWidth() - 1)
}


object LogicalOp {
  val Op = OpGen2({ new LogicalOp(_)}) _
  def apply(x: Node, y: Node, op: String): Bool = {
    val node = op match {
      case "===" => Op("==",  fixWidth(1), x, y)
      case "!="  => Op("!=",  fixWidth(1), x, y)
      case "<"   => Op("<",   fixWidth(1), x, y)
      case "<="  => Op("<=",  fixWidth(1), x, y)
      case "s<"  => Op("s<",  fixWidth(1), x, y)
      case "s<=" => Op("s<=", fixWidth(1), x, y)
      case "f==" => Op("f==", fixWidth(1), x, y)
      case "f!=" => Op("f!=", fixWidth(1), x, y)
      case "f>"  => Op("f>",  fixWidth(1), x, y)
      case "f<"  => Op("f<",  fixWidth(1), x, y)
      case "f<=" => Op("f<=", fixWidth(1), x, y)
      case "f>=" => Op("f>=", fixWidth(1), x, y)
      case "d==" => Op("d==", fixWidth(1), x, y)
      case "d!=" => Op("d!=", fixWidth(1), x, y)
      case "d>"  => Op("d>",  fixWidth(1), x, y)
      case "d<"  => Op("d<",  fixWidth(1), x, y)
      case "d<=" => Op("d<=", fixWidth(1), x, y)
      case "d>=" => Op("d>=", fixWidth(1), x, y)
      case any   => throw new Exception("Unrecognized operator " + op);
    }
    Bool(OUTPUT).fromNode(node)
  }
}

object ReductionOp {
  val Op = OpGen1({ new ReductionOp(_)}) _
  def apply(x: Node, op: String): Node = {
    op match {
      case "^" => Op("^", fixWidth(1), x)
      case any => throw new Exception("Unrecognized operator " + op)
    }
  }
}

object Op {
  val logicalChars = """^([!=<>]=)|([<>])$""".r
  def apply(name: String, widthInfer: (=> Node) => Width, a: Node, b: Node): Node = {
    // It's a binary operator. Is it a logical op?
    if (logicalChars.findFirstIn(name).nonEmpty) {
      OpGen2({ new LogicalOp(_)})(name, widthInfer, a, b)
    } else {
      OpGen2({ new BinaryOp(_)})(name, widthInfer, a, b)
    }
  }
  def OpGen2(makeObj: String => Op)(name: String, widthInfer: (=> Node) => Width, a: Node, b: Node): Node = {
    val (a_lit, b_lit) = (a.litOf, b.litOf)
    if (a_lit != null) name match {
      case "==" => if (a_lit.isZ) return zEquals(b, a)
      case "!=" => if (a_lit.isZ) return !zEquals(b, a)
      case "<<" | ">>" | "s>>" => if (a_lit.value == 0) return Literal(0)
      case _ => ;
    }
    if (b_lit != null) name match {
      case "==" => if (b_lit.isZ) return zEquals(a, b)
      case "!=" => if (b_lit.isZ) return !zEquals(a, b)
      case _ => ;
    }
    if (a_lit != null && b_lit != null && a_lit.isKnownWidth && b_lit.isKnownWidth) {
      val (aw, bw) = (a_lit.needWidth(), b_lit.needWidth());
      val (av, bv) = (a_lit.value, b_lit.value);
      name match {
        case "==" => return Literal(if (av == bv) 1 else 0)
        case "!=" => return Literal(if (av != bv) 1 else 0);
        case "<"  => return Literal(if (av <  bv) 1 else 0);
        case "<=" => return Literal(if (av <= bv) 1 else 0);
        case "##" => return Literal(av << bw | bv, aw + bw);
        case "+"  => return Literal(av + bv, max(aw, bw))
        case "-"  => return Literal(av - bv, max(aw, bw))
        case "|"  => return Literal(av | bv, max(aw, bw));
        case "&"  => return Literal(av & bv, max(aw, bw));
        case "^"  => return Literal(av ^ bv, max(aw, bw));
        case "<<" => return Literal(av << bv.toInt, aw + bv.toInt);
        case ">>" => return Literal(av >> bv.toInt, aw - bv.toInt);
        case _ => ;
      }
    }
    if (a.isInstanceOf[Flo] && b.isInstanceOf[Flo]) {
      if (a_lit != null && b_lit != null) {
      val (fa_val, fb_val) = (a_lit.floLitValue, b_lit.floLitValue)
      name match {
        case "f+" => return Flo(fa_val + fb_val);
        case "f-" => return Flo(fa_val - fb_val);
        case "f*" => return Flo(fa_val * fb_val);
        case "f/" => return Flo(fa_val / fb_val);
        case "f%" => return Flo(fa_val % fb_val);
        case "f==" => return Bool(fa_val == fb_val);
        case "f!=" => return Bool(fa_val != fb_val);
        case "f>" => return Bool(fa_val > fb_val);
        case "f<" => return Bool(fa_val < fb_val);
        case "f>=" => return Bool(fa_val >= fb_val);
        case "f<=" => return Bool(fa_val <= fb_val);
        case _ => ;
      }
      } else if (a_lit != null) { 
        val fa_val = a_lit.floLitValue
        if (fa_val == 0.0) {
          name match {
            case "f+" => return b;
            case "f*" => return Flo(0.0.toFloat);
            case "f/" => return Flo(0.0.toFloat);
            case _ => ;
          }
        } else if (fa_val == 1.0) {
          name match {
            case "f*" => return b;
            case _ => ;
          }
        }        
      } else if (b_lit != null) { 
        val fb_val = b_lit.floLitValue
        if (fb_val == 0.0) {
          name match {
            case "f+" => return a;
            case "f*" => return Flo(0.0.toFloat);
            case _ => ;
          }
        } else if (fb_val == 1.0) {
          name match {
            case "f*" => return a;
            case "f/" => return a;
            case "f%" => return a;
            case _ => ;
          }
        }        
      }
    }
      
    if (a.isInstanceOf[Dbl] && b.isInstanceOf[Dbl]) {
      if (a_lit != null && b_lit != null) {
      val (fa_val, fb_val) = (a_lit.dblLitValue, b_lit.dblLitValue)
        // println(" FOLDING " + name + " " + fa_val + " " + fb_val);
      name match {
        case "d+" => return Dbl(fa_val + fb_val);
        case "d-" => return Dbl(fa_val - fb_val);
        case "d*" => return Dbl(fa_val * fb_val);
        case "d/" => return Dbl(fa_val / fb_val);
        case "d%" => return Dbl(fa_val % fb_val);
        case "d==" => return Bool(fa_val == fb_val);
        case "d!=" => return Bool(fa_val != fb_val);
        case "d>" => return Bool(fa_val > fb_val);
        case "d<" => return Bool(fa_val < fb_val);
        case "d>=" => return Bool(fa_val >= fb_val);
        case "d<=" => return Bool(fa_val <= fb_val);
        case _ => ;
      }
    } else if (a_lit != null) { 
      val fa_val = a_lit.dblLitValue
      // println("FA " + fa_val + " NAME " + name);
      if (fa_val == 0.0) {
        // println("FOLDING " + name);
        name match {
          case "d+" => return b;
          case "d*" => return Dbl(0.0);
          case "d/" => return Dbl(0.0);
          case "d%" => return Dbl(0.0);
          case _ => ;
        }
      } else if (fa_val == 1.0) {
        // println("FOLDING " + name);
        name match {
          case "d*" => return b;
          case _ => ;
        }
      }        
    } else if (b_lit != null) { 
      val fb_val = b_lit.dblLitValue
      // println("FB " + fb_val + " NAME " + name);
      if (fb_val == 0.0) {
        // println("FOLDING " + name);
        name match {
          case "d+" => return a;
          case "d*" => return Dbl(0.0);
          case _ => ;
        }
      } else if (fb_val == 1.0) {
        // println("FOLDING " + name);
        name match {
          case "d*" => return a;
          case "d/" => return a;
          case "d%" => return a;
          case _ => ;
        }
      }        
    }

    }
    if (Driver.backend.isInstanceOf[CppBackend] || Driver.backend.isInstanceOf[FloBackend] ||
        Driver.isBackannotating) {
      def signAbs(x: Node): (Bool, UInt) = {
        val f = x.asInstanceOf[SInt]
        val s = f < SInt(0)
        (s, Mux(s, -f, f).toUInt)
      }
      name match {
        case "s<" | "s<=" =>
          if (name != "s<" || b.litOf == null || b.litOf.value != 0) {
            val fixA = a.asInstanceOf[SInt]
            val fixB = b.asInstanceOf[SInt]
            val msbA = fixA < SInt(0)
            val msbB = fixB < SInt(0)
            val ucond = Bool(OUTPUT).fromNode(LogicalOp(fixA, fixB, name.tail))
            return Mux(msbA === msbB, ucond, msbA)
          }
        case "s*s" | "s*u" =>
          val (signA, absA) = signAbs(a)
          val (signB, absB) = signAbs(b)
          val prod = absA * absB
          return Mux(signA ^ signB, -prod, prod)
        case "s/s" =>
          val (signA, absA) = signAbs(a)
          val (signB, absB) = signAbs(b)
          val quo = absA / absB
          return Mux(signA != signB, -quo, quo)
        case "s%s" =>
          val (signA, absA) = signAbs(a)
          val (signB, absB) = signAbs(b)
          val rem = absA % absB
          return Mux(signA, -rem, rem)
        case "%" =>
          val (au, bu) = (a.asInstanceOf[UInt], b.asInstanceOf[UInt])
          return Op("-", widthInfer, au, au/bu*bu)
        case _ =>
      }
    }
    if (a.isLit && a.litOf.isZ || b.isLit && b.litOf.isZ)
      ChiselError.error({"Operator " + name + " with inputs " + a + ", " + b + " does not support literals with ?"});
    val res = makeObj(name)
    res.init("", widthInfer, a, b);
    res
  }
  def apply(name: String, widthInfer: (=> Node) => Width, a: Node): Node = {
    // It's a unary operator.
    OpGen1({ new UnaryOp(_)})(name, widthInfer, a)
  }

  def OpGen1(makeObj: String => Op)(name: String, widthInfer: (=> Node) => Width, a: Node): Node = {
      if (a.litOf != null) {
        if (a.litOf.isZ)
          ChiselError.error({"Operator " + name + " with input " + a + " does not support literals with ?"});
        val wa = a.litOf.needWidth()
        name match {
          case "~" => return Literal((-a.litOf.value-1)&((BigInt(1) << wa)-1), wa);
          case _ => ;
        }
      }
      val a_lit = a.litOf
    if (a.isInstanceOf[Dbl]) { 
      if (a_lit != null) {
      val fa_val = a_lit.dblLitValue
      name match {
        case "dsin" => return Dbl(Math.sin(fa_val));
        case "dcos" => return Dbl(Math.cos(fa_val));
        case "dtan" => return Dbl(Math.tan(fa_val));
        case "dasin" => return Dbl(Math.asin(fa_val));
        case "dacos" => return Dbl(Math.acos(fa_val));
        case "datan" => return Dbl(Math.atan(fa_val));
        case "dsqrt" => return Dbl(Math.sqrt(fa_val));
        case "dlog" => return Dbl(Math.log(fa_val));
        case "dfloor" => return Dbl(Math.floor(fa_val));
        case "dceil" => return Dbl(Math.ceil(fa_val));
        case "dround" => return Dbl(Math.round(fa_val));
        case "dToFix" => return Literal(fa_val.toInt);
        case _ => ;
      }
      }
    }
    if (a.isInstanceOf[Flo]) {
      if (a_lit != null) {
      val fa_val = a_lit.floLitValue
      name match {
        case "fsin" => return Flo(Math.sin(fa_val).toFloat);
        case "fcos" => return Flo(Math.cos(fa_val).toFloat);
        case "ftan" => return Flo(Math.tan(fa_val).toFloat);
        case "fasin" => return Flo(Math.asin(fa_val).toFloat);
        case "facos" => return Flo(Math.acos(fa_val).toFloat);
        case "fatan" => return Flo(Math.atan(fa_val).toFloat);
        case "fsqrt" => return Flo(Math.sqrt(fa_val).toFloat);
        case "flog" => return Flo(Math.log(fa_val).toFloat);
        case "ffloor" => return Dbl(Math.floor(fa_val).toFloat);
        case "fceil" => return Dbl(Math.ceil(fa_val).toFloat);
        case "fround" => return Dbl(Math.round(fa_val).toFloat);
        case "fToFix" => return Literal(fa_val.toLong);
        case _ => ;
      }
      }
    }
    val res = makeObj(name)
    res.init("", widthInfer, a);
    res
  }

  private def zEquals(a: Node, b: Node) = {
    val (bits, mask, swidth) = parseLit(b.litOf.name)
    val Op = OpGen2({ new BinaryOp(_)}) _
    UInt(Op("==", fixWidth(1), Op("&", maxWidth _, a, Literal(BigInt(mask, 2))), Literal(BigInt(bits, 2))))
  }
}

abstract class Op extends Node {
  val op: String

  override def toString: String =
    if (inputs.length == 1) {
      op + "(" + inputs(0) + ")"
    } else {
      op + " [ " + inputs(0) + "]" + op + "[  " + inputs(1) + "]"
      // "[ " + inputs(0) + "\n]\n  " + op + "\n" + "[  " + inputs(1) + "\n]"
    }

  override def forceMatchingWidths {
    if (inputs.length == 2) {
      if (List("|", "&", "^", "+", "-").contains(op)) {
        if (inputs(0).width != width) inputs(0) = inputs(0).matchWidth(width)
        if (inputs(1).width != width) inputs(1) = inputs(1).matchWidth(width)
      } else if (List("==", "!=", "<", "<=").contains(op)) {
        val w = max(inputs(0).needWidth(), inputs(1).needWidth())
        if (inputs(0).needWidth() != w) inputs(0) = inputs(0).matchWidth(Width(w))
        if (inputs(1).needWidth() != w) inputs(1) = inputs(1).matchWidth(Width(w))
      }
    }
  }

  override def canCSE: Boolean = true
  override def equalsForCSE(x: Node): Boolean = x match {
    case that: Op => this.op == that.op && CSE.inputsEqual(this, that)
    case _ => false
  }

  def lower: Node = throw new Exception("lowering " + op + " is not supported")
  def identityFromNode: Int = op match {
    case "<<"  => 0
    case ">>"  => 0
    case "s>>" => 0
    case "+"   => 0
    case "*"   => 1
    case "s*s" => 1
    case "s*u" => 1
    case "/"   => 1
    case "s/s" => 1
    case "%"   => 1
    case "s%s" => 1
    case "^"   => 0
    case "-"   => 0
    case "##"  => 0
    case "&"   => 1
    case "|"   => 0
    case "f+"  => 0
    case "f-"  => 0
    case "f*"  => 1
    case "f/"  => 1
    case "f%"  => 1
    case "fpow"  => 1
    case "d+"  => 0
    case "d-"  => 0
    case "d*"  => 1
    case "d/"  => 1
    case "d%"  => 1
    case "dpow"  => 1
  }

  // Transform an operator with one or more zero width children into an operator without.
  override def W0Wtransform(): Option[Node] = this match {
    case u: UnaryOp => {
      None
    }
    case l: LogicalOp => {
      None
    }
    case b: BinaryOp => {
      // Replace any zero width child nodes with the identity element for this operator.
      // TODO: We may need to refine this since not all children are created equal.
      for (i <- 0 until inputs.length) {
        if (inputs(i).getWidth == 0) {
          inputs(i) = UInt(identityFromNode, 1)
        }
      }
      None
    }
  }
}
case class LogicalOp(val op: String) extends Op
case class BinaryOp(val op: String) extends Op
case class UnaryOp(val op: String) extends Op
case class ReductionOp(val op: String) extends Op
