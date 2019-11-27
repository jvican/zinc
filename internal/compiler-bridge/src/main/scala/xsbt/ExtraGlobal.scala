package xsbt

/*
 * Marker trait used to facilitate integration of Triplequote Hydra with Bloop.
 *
 * Note: The bloop-hydra-bridge source component provides an alternative implementation
 * of this trait so if you need to modify this file please get in touch with
 * [[https://github.com/dotta/ Mirco Dotta]] or [[https://github.com/dragos/ Iulian Dragos]].
 */
trait ExtraGlobal { self: CallbackGlobal =>
  override lazy val loaders = new {
    val global: self.type = self
    val platform: self.platform.type = self.platform
  } with ZincSymbolLoaders
}
