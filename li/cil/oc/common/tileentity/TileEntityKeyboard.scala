package li.cil.oc.common.tileentity

import cpw.mods.fml.common.network.Player
import li.cil.oc.api.{INetworkNode, INetworkMessage}

class TileEntityKeyboard extends TileEntityRotatable with INetworkNode {
  def id = 0

  override def receive(message: INetworkMessage) = message.data match {
    case Array(name: String, p: Player, c: Character) if name == "keyboard.keyDown" => {
      // TODO check if player is close enough and only consume message if so
      network.sendToAll(this, "signal", "key_down", c)
      message.cancel()
    }
    case Array(name: String, p: Player, c: Character) if name == "keyboard.keyUp" => {
      // TODO check if player is close enough and only consume message if so
      network.sendToAll(this, "signal", "key_up", c)
      message.cancel()
    }
    case _ => // Ignore.
  }
}