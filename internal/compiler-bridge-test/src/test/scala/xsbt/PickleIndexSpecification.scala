package xsbt

import java.net.URI

import sbt.internal.inc.UnitSpec
import xsbti.TestCallback

class PickleIndexSpecification extends UnitSpec {
  "Pickle index" should "be collected by Zinc" in {
    if (scala.util.Properties.versionNumberString.startsWith("2.10")) ()
    else runTest
  }

  def runTest(): Unit = {
    val pickleArgs = List("-Ygenerate-pickles")
    var generatedIndex: Boolean = false
    var handles: List[URI] = Nil
    object IndexTestCallback extends TestCallback {
      override def picklerPhaseCompleted(uri: URI): Unit = {
        generatedIndex = true
        handles = uri :: handles
        ()
      }
    }

    val sourceA =
      """
        |class A {
        |  class AA
        |}
        |object A {
        |  trait Hoo {
        |    type w00t
        |  }
        |  class Nested {
        |    def greeting: String = "Hello World"
        |    type w00t = Int
        |  }
        |}
        |
        |object TestOnlyObject {
        |  val a = "akjsd;flkajsdkf"
        |}
        |
        |package foo {
        |  class B {
        |    class B2
        |  }
        |  object B {
        |    class B3
        |  }
        |}
        |
      """.stripMargin

    val sourceB =
      """
        |object UseSite {
        |  val a = new A
        |  val aa = new a.AA
        |  val an = new A.Nested
        |  println(an.greeting)
        |  println(1.isInstanceOf[an.w00t])
        |  val b = new foo.B
        |}
      """.stripMargin

    val sourceC =
      """object UseSite2 {
        |  val a = new A
        |}
      """.stripMargin
    val sourceD =
      """
        |object UseSiteUseSite {
        |  println(UseSite.b)
        |}
      """.stripMargin

    val sourceE =
      """
        |object UseSiteUseSite {
        |  println(TestOnlyObject.a)
        |}
      """.stripMargin

    val compiler = new ScalaCompilerForUnitTesting
    val projectA = compiler.Project(List(sourceA), IndexTestCallback, pickleArgs)
    val projectB = compiler.Project(List(sourceB), IndexTestCallback, pickleArgs)
    val projectC = compiler.Project(List(sourceC), IndexTestCallback, pickleArgs)
    val projectD = compiler.Project(List(sourceD), IndexTestCallback, pickleArgs)
    val projectE = compiler.Project(List(sourceE), IndexTestCallback, pickleArgs)

    // The compilation is sequential (whereas it could be in parallel), but there
    // is no resource sharing (e.g. class dirs) so it emulates a real-world scenario.
    val compiler.CompilationResult(_, _, compilerA) =
      compiler.compileProject(projectA, Nil, Nil)
    println("Project A is compiled.")
    val compiler.CompilationResult(_, _, compilerB) =
      compiler.compileProject(projectB, Nil, handles)
    println("Project B is compiled.")
    compiler.compileProject(projectC, Nil, handles)
    println("Project C is compiled.")
    compiler.compileProject(projectD, Nil, handles)
    println("Project D is compiled.")
    compiler.compileProject(projectE, Nil, handles)
    println("Project E is compiled.")

    // Let's create a clash in the
    val sourceA2 =
      """
        |class A {
        |  class AA
        |}
        |object A {
        |  trait Hoo {
        |    type w00t
        |  }
        |  class Nested2 {
        |    def greeting2: String = "Hello World"
        |    type w00t = Int
        |  }
        |}
        |
        |object TestOnlyObject {
        |  val a = "akjsd;flkajsdkf"
        |}
        |
        |package foo {
        |  class B2
        |}
      """.stripMargin

    val sourceB2 =
      """
        |object UseSite {
        |  val an = new A.Nested2
        |  println(an.greeting2)
        |  class B3 extends foo.B2
        |  println(new B3)
        |}
      """.stripMargin

    var handles2: List[URI] = Nil
    object IndexTestCallback2 extends TestCallback {
      override def picklerPhaseCompleted(uri: URI): Unit = {
        generatedIndex = true
        handles2 = uri :: handles2
        ()
      }
    }

    // Check that the symbol table is not shared across different compiler instances
    val projectA2 = compiler.Project(List(sourceA2), IndexTestCallback2, pickleArgs)
    val projectB2 = compiler.Project(List(sourceB2), IndexTestCallback2, pickleArgs)
    compiler.compileProject(projectA2, Nil, Nil)
    println("Project A is compiled again with modifications.")
    compiler.compileProject(projectB2, Nil, handles2)
    println("Project B is compiled again with modifications.")

    //val results = compiler.compileProjectsLinearly(List(projectA, projectB))
    assert(generatedIndex, "Compiler did not generate a pickle index.")
  }
}
