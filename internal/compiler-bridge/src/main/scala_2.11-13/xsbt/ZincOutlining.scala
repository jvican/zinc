package xsbt

trait ZincOutlining {
  self: ZincCompiler =>

  override def newUnitParser(unit: CompilationUnit): syntaxAnalyzer.UnitParser = {
    if (settings.Youtline.value) new ZincUnitParser(unit)
    else new syntaxAnalyzer.UnitParser(unit)
  }

  import syntaxAnalyzer.UnitParser
  final class ZincUnitParser(unit: CompilationUnit) extends UnitParser(unit) {
    def diff(outlined: Tree, normal: Tree): Unit = {
      import com.github.difflib.{DiffUtils, UnifiedDiffUtils}
      import scala.collection.JavaConverters._
      import java.nio.file.{ Files, Paths }
      val original = showCode(normal)
      val path = unit.source.file.absolute.canonicalPath
      val diffPath = s"${path}.diff"
      val patch = DiffUtils.diff(original, showCode(outlined))
      val originalLines = original.split(System.lineSeparator()).toList.asJava
      val diffs = UnifiedDiffUtils.generateUnifiedDiff(path, diffPath, originalLines, patch, 0)
      val diff = diffs.asScala.mkString("\n")
      Files.write(Paths.get(diffPath), diff.getBytes)
      inform(s"Generated $diffPath")
    }

    override def parse(): Tree = {
      val outlined = super.parse()
      if (!settings.YoutlineDiff.value) outlined
      else {
        val normal = new UnitParser(unit).parse()
        diff(outlined, normal)
        outlined
      }
    }

    private var insideTrait: Boolean = false
    private def containsSuperAccessor(body: Tree): Boolean = {
      body.collect {
        case a @ Apply(Super(_, mix), _) if insideTrait || mix.nonEmpty  => a
        case s @ Select(Super(_, mix), _) if insideTrait || mix.nonEmpty => s
      }.nonEmpty
    }

    private def canDropBody(definition: ValOrDefDef): Boolean = ! {
      definition.tpt.isEmpty || // Cannot drop if we need scalac to infer the type
        definition.rhs.isEmpty || // Cannot drop if body is already empty
        //definition.name == nme.ANON_FUN_NAME || // Cannot drop because they constrain type args
        definition.mods.isFinal && definition.rhs.isInstanceOf[Literal] || // Constant folding
        containsSuperAccessor(definition.rhs) // Cannot drop super accessors, they affect public API
    }

    // To make sure that the previous term name works and can always be found
    import _root_.scala.Predef.{ ??? => _ }
    private val UndefinedTree: Tree = q"_root_.scala.Predef.???"

    override def patDefOrDcl(pos: RunId, mods: Modifiers): List[Tree] = {
      super.patDefOrDcl(pos, mods).mapConserve {
        case vd: ValDef if canDropBody(vd) => vd.copy(rhs = UndefinedTree)
        case t                             => t
      }
    }

    private val TailRecName = TypeName("tailrec")
    def stripTailRec(mods: Modifiers): Modifiers = {
      mods.mapAnnotations { annotations =>
        annotations.filter {
          case Apply(Select(New(Ident(TailRecName)), _), _)     => false
          case Apply(Select(New(Select(_, TailRecName)), _), _) => false
          case _                                                => true
        }
      }
    }

    override def funDefOrDcl(start: RunId, mods: Modifiers): Tree = {
      super.funDefOrDcl(start, mods) match {
        case dd: DefDef if canDropBody(dd) =>
          dd.copy(mods = stripTailRec(dd.mods), rhs = UndefinedTree)
        case t => t
      }
    }

    def pruneExpr(tree: Tree): List[Tree] = {
      tree match {
        case Ident(_) | Apply(_, _) | Select(_, _) | TypeApply(_, _) | This(_) => Nil
        case t @ (Block(_, _) | Try(_, _, _)) if !containsSuperAccessor(t)     => Nil
        case t                                                                 => List(t)
      }
    }

    override def templateStatSeq(isPre: Boolean): (ValDef, List[Tree]) = {
      val (selfDecl, trees) = super.templateStatSeq(isPre)
      (selfDecl, trees.flatMap(pruneExpr))
    }

    override def templateStat: PartialFunction[syntaxAnalyzer.Token, List[Tree]] = {
      super.templateStat.andThen(_.flatMap(pruneExpr))
    }

    override def classDef(start: syntaxAnalyzer.Offset, mods: Modifiers): ClassDef = {
      val isTrait = mods.isTrait
      if (isTrait) insideTrait = true
      val classDef = super.classDef(start, mods)
      if (isTrait) insideTrait = false
      classDef
    }
  }

}
