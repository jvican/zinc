package xsbt

import scala.tools.nsc.symtab.SymbolLoaders

// 2.10 doesn't implement pipelined compilation
abstract class ZincSymbolLoaders extends SymbolLoaders
