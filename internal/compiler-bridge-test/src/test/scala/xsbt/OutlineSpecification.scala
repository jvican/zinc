package xsbt

import sbt.internal.inc.UnitSpec
import xsbti.TestCallback

class OutlineSpecification extends UnitSpec {
  "Pickle index" should "be collected by Zinc" in {
    if (scala.util.Properties.versionNumberString.startsWith("2.10")) ()
    else runTest
  }

  def runTest(): Unit = {

    val sourceA =
      """
        |class A {
        |  println(new TemplateExpr)
        |  new TemplateExpr
        |
        |  def hello2(o: AnyRef): Int = {
        |    @scala.annotation.tailrec def t(i: Int): Int = {
        |      if (t == 0) t
        |      else t(i-1)
        |    }
        |
        |    println(new InsideDef)
        |    try {
        |      println(new InsideDef)
        |      val _ = new InsideDef
        |      1
        |    } catch {
        |      case t: Throwable => 2
        |    }
        |  }
        |
        |  val hello: Int = {
        |    new InsideDef
        |    1
        |  }
        |
        |  @scala.annotation.tailrec def t(i: Int): Int = {
        |    if (i == 0) i
        |    else t(i-1)
        |  }
        |
        |  import scala.annotation.tailrec
        |  @tailrec def t2(i: Int): Int = {
        |    if (i == 0) i
        |    else t2(i-1)
        |  }
        |}
        |
        |trait Base {
        |  def base: Int = 1
        |  def id(i: Int): Int = i
        |}
        |
        |trait T extends Base {
        |  def superAccessorApply: Int = {
        |    println(new SuperAccessorApply)
        |    super.id(1)
        |  }
        |
        |  def superAccessorSelect: Int = {
        |    println(new SuperAccessorSelect)
        |    super.base
        |  }
        |}
        |
        |object A {
        |  println(new TemplateExpr)
        |  new TemplateExpr
        |}
      """.stripMargin

    val sourceB =
      """
        |class InsideDef
        |class TemplateExpr
        |class SuperAccessorApply
        |class SuperAccessorSelect
      """.stripMargin

    val outlineArgs = List("-Youtline", "-Youtline-diff", "-Ystatistics")
    object ParallelTestCallback extends TestCallback
    val compiler = new ScalaCompilerForUnitTesting
    val projectA = compiler.Project(List(sourceA, sourceB), ParallelTestCallback, outlineArgs)

    // The compilation is sequential (whereas it could be in parallel), but there
    // is no resource sharing (e.g. class dirs) so it emulates a real-world scenario.
    val compiler.CompilationResult(_, testCallback, _) =
      compiler.compileProject(projectA, Nil, Nil)
    val dependencies = TestCallback.fromCallback(testCallback)
    assert(dependencies.memberRef("A") === Set.empty)
    assert(dependencies.memberRef("T") === Set("SuperAccessorApply", "SuperAccessorSelect"))
    println("Project A is compiled.")
  }
}
