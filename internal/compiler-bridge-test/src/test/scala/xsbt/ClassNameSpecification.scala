package xsbt

import org.scalactic.source.Position
import sbt.internal.inc.UnitSpec

class ClassNameSpecification extends UnitSpec {
  "ClassName" should "create correct binary names for top level object" in {
    expectBinaryClassNames("object A", Set("A" -> "A", "A" -> "A$"))
  }

  it should "create binary names for top level class" in {
    expectBinaryClassNames("class A", Set("A" -> "A"))
  }

  it should "create binary names for top level companions" in {
    val src = "class A; object A"
    expectBinaryClassNames(src, Set("A" -> "A", "A" -> "A$"))
  }

  it should "create binary names for case classes with no companions" in {
    expectBinaryClassNames(
      "case class LonelyCaseClass(paramA: String)",
      Set("LonelyCaseClass" -> "LonelyCaseClass", "LonelyCaseClass" -> "LonelyCaseClass$")
    )
  }

  it should "create binary names for case classes with companions" in {
    expectBinaryClassNames(
      "case class LonelyCaseClass2(paramA: String);object LonelyCaseClass2 { val z: Int = 1 }",
      Set("LonelyCaseClass2" -> "LonelyCaseClass2", "LonelyCaseClass2" -> "LonelyCaseClass2$")
    )
  }

  it should "create correct binary names for nested object" in {
    val src = "object A { object C { object D } }; class B { object E }"
    expectBinaryClassNames(
      src,
      Set(
        "A" -> "A$",
        "A" -> "A",
        "A.C" -> "A$C$",
        "A.C.D" -> "A$C$D$",
        "B" -> "B",
        "B.E" -> "B$E$"
      )
    )
  }

  it should "handle advanced scenarios of nested classes and objects" in {
    val src =
      """
        |package foo.bar
        |
        |class A {
        |  class A2 {
        |    class A3 {
        |      object A4
        |    }
        |  }
        |}
        |object A {
        |  class B
        |  object B {
        |    class C
        |  }
        |}
      """.stripMargin

    expectBinaryClassNames(
      src,
      Set(
        "foo.bar.A" -> "foo.bar.A",
        "foo.bar.A" -> "foo.bar.A$",
        "foo.bar.A.A2" -> "foo.bar.A$A2",
        "foo.bar.A.A2.A3" -> "foo.bar.A$A2$A3",
        "foo.bar.A.A2.A3.A4" -> "foo.bar.A$A2$A3$A4$",
        "foo.bar.A.B" -> "foo.bar.A$B",
        "foo.bar.A.B" -> "foo.bar.A$B$",
        "foo.bar.A.B.C" -> "foo.bar.A$B$C"
      )
    )
  }

  it should "create a binary name for both class of the package objects and its classes" in {
    val src = "package object po { class B; object C }"
    expectBinaryClassNames(
      src,
      Set(
        "po.package" -> "po.package",
        "po.package" -> "po.package$",
        "po.B" -> "po.package$B",
        "po.C" -> "po.package$C$",
      )
    )
  }

  it should "create a binary name for a trait" in {
    // we do not track $impl classes because nobody can depend on them directly
    expectBinaryClassNames("trait A", Set("A" -> "A"))
  }

  it should "not create binary names for local classes" in {
    val src = """
      |class Container {
      |  def foo = {
      |    class C
      |  }
      |  def bar = {
      |    // anonymous class
      |    new T {}
      |  }
      |}
      |
      |trait T
      |""".stripMargin

    expectBinaryClassNames(src, Set("Container" -> "Container", "T" -> "T"))
  }

  private def expectBinaryClassNames(src: String, expectedNames: Set[(String, String)])(
      implicit p: Position): Unit = {
    val compilerForTesting = new ScalaCompilerForUnitTesting
    val binaryClassNames = compilerForTesting.extractBinaryClassNamesFromSrc(src)

    if (binaryClassNames === expectedNames) ()
    else {
      val missing = binaryClassNames.diff(expectedNames).mkString
      val extra = expectedNames.diff(binaryClassNames).mkString
      fail(
        if (missing.nonEmpty && extra.nonEmpty) s"Missing names $missing; extra names $extra"
        else if (missing.isEmpty) s"Extra names ${extra}"
        else s"Missing names ${extra}"
      )
    }
  }
}
