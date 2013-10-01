package li.cil.oc.common.tileentity

import java.util.concurrent.atomic.AtomicBoolean
import li.cil.oc.api.network.Message
import li.cil.oc.client.component.{Computer => ClientComputer}
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.server.component.Computer.{Environment => ComputerEnvironment}
import li.cil.oc.server.component.RedstoneEnabled
import li.cil.oc.server.component.{Computer => ServerComputer}
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.ForgeDirection

class Computer(isClient: Boolean) extends Rotatable with ComputerEnvironment with ComponentInventory with RedstoneEnabled {
  def this() = this(false)

  protected val computer =
    if (isClient) new ClientComputer(this)
    else new ServerComputer(this)

  private val hasChanged = new AtomicBoolean(true)

  private var isRunning = false

  // ----------------------------------------------------------------------- //
  // NetworkNode
  // ----------------------------------------------------------------------- //

  override def receive(message: Message) = {
    super.receive(message)
    message.data match {
      // The isRunning check is here to avoid component_* signals being
      // generated while loading a chunk.
      case Array() if message.name == "network.connect" && isRunning =>
        computer.signal("component_added", message.source.address.get); None
      case Array() if message.name == "network.disconnect" && isRunning =>
        computer.signal("component_removed", message.source.address.get); None
      case Array(oldAddress: Integer) if message.name == "network.reconnect" && isRunning =>
        computer.signal("component_changed", message.source.address.get, oldAddress); None
      case Array(name: String, args@_*) if message.name == "computer.signal" =>
        computer.signal(name, List(message.source.address.get) ++ args: _*); None
      case Array() if message.name == "computer.start" =>
        Some(Array(turnOn().asInstanceOf[Any]))
      case Array() if message.name == "computer.stop" =>
        Some(Array(turnOff().asInstanceOf[Any]))
      case Array() if message.name == "computer.running" =>
        Some(Array(isOn.asInstanceOf[Any]))
      case _ => None
    }
  }

  // ----------------------------------------------------------------------- //
  // General
  // ----------------------------------------------------------------------- //

  def turnOn() = computer.start()

  def turnOff() = computer.stop()

  def isOn = computer.isRunning

  def isOn_=(value: Boolean) = {
    computer.asInstanceOf[ClientComputer].isRunning = value
    worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord)
    this
  }

  override def readFromNBT(nbt: NBTTagCompound) = {
    super.readFromNBT(nbt)
    load(nbt.getCompoundTag("data"))
    computer.load(nbt.getCompoundTag("computer"))
  }

  override def writeToNBT(nbt: NBTTagCompound) = {
    super.writeToNBT(nbt)

    val dataNbt = new NBTTagCompound
    save(dataNbt)
    nbt.setCompoundTag("data", dataNbt)

    val computerNbt = new NBTTagCompound
    computer.save(computerNbt)
    nbt.setCompoundTag("computer", computerNbt)
  }

  override def updateEntity() = if (!worldObj.isRemote) {
    computer.update()
    if (hasChanged.get) {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
    }
    if (isRunning != computer.isRunning) {
      isRunning = computer.isRunning
      if (isRunning)
        network.foreach(_.sendToVisible(this, "computer.started"))
      else
        network.foreach(_.sendToVisible(this, "computer.stopped"))
      ServerPacketSender.sendComputerState(this, isRunning)
    }
  }

  override def validate() = {
    super.validate()
    if (worldObj.isRemote)
      ClientPacketSender.sendComputerStateRequest(this)
  }

  // ----------------------------------------------------------------------- //
  // RedstoneEnabled
  // ----------------------------------------------------------------------- //

  def input(side: ForgeDirection): Int = worldObj.isBlockProvidingPowerTo(
    xCoord + side.offsetX, yCoord + side.offsetY, zCoord + side.offsetZ, side.getOpposite.ordinal)

  // TODO output

  // ----------------------------------------------------------------------- //
  // Interfaces and updating
  // ----------------------------------------------------------------------- //

  override def isUseableByPlayer(player: EntityPlayer) =
    worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this &&
      player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 64

  override def world = worldObj

  override def markAsChanged() = hasChanged.set(true)
}