package org.lambda

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.ProjectionUtils
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.event.listener.listener
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.server.SPacketSoundEffect
import net.minecraft.util.SoundEvent
import net.minecraft.util.math.BlockPos
import net.minecraftforge.event.world.NoteBlockEvent
import org.lambda.util.Note
import org.lwjgl.opengl.GL11
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log2
import kotlin.math.roundToInt

internal object NoteESP: PluginModule(
    name = "NoteESP",
    category = Category.RENDER,
    description = "Shows note block pitch",
    pluginMain = MusicToolsPlugin
) {
    private val filled by setting("Filled", true, description = "Renders surfaces")
    private val outline by setting("Outline", true, description = "Renders outline")
    private val alphaFilled by setting("Alpha Filled", 26, 0..255, 1, { filled }, description = "Alpha for surfaces")
    private val alphaOutline by setting("Alpha Outline", 26, 0..255, 1, { outline }, description = "Alpha for outline")
    private val thickness by setting("Outline Thickness", 2f, .25f..4f, .25f, { outline })
    private val textScale by setting("Text Scale", 1f, .0f..4f, .25f)
    private val reset = setting("Reset", false, description = "Resets cached notes")
    private val debug by setting("Debug", false, description = "Debug messages in chat")

    private val cachedNotes = ConcurrentHashMap<BlockPos, Note>()
    private val renderer = ESPRenderer()

    init {
        reset.consumers.add { _, it ->
            if (it) {
                cachedNotes.clear()
            }
            false
        }

        listener<PacketEvent.Receive> { event ->
            if (event.packet is SPacketSoundEffect) {
                val packet = (event.packet as SPacketSoundEffect)

                val instrument = getInstrument(packet.sound) ?: return@listener
                val pos = BlockPos(packet.x, packet.y, packet.z)
                val note = Note.values()[(log2(packet.pitch.toDouble()) * 12.0).roundToInt() + 12]

                cachedNotes[pos] = note

                if (debug) {
                    MessageSendHelper.sendChatMessage("Instrument: ${instrument.name} Pos: (${packet.x},${packet.y},${packet.z}) Pitch: ${note.name}")
                }
            }
        }

        listener<RenderWorldEvent> {
            renderer.aFilled = if (filled) alphaFilled else 0
            renderer.aOutline = if (outline) alphaOutline else 0
            renderer.thickness = thickness

            cachedNotes.forEach {
                renderer.add(it.key, it.value.color)
            }

            renderer.render(true)
        }

        listener<RenderOverlayEvent> {
            GlStateUtils.rescaleActual()

            cachedNotes.forEach {
                GL11.glPushMatrix()

                val screenPos = ProjectionUtils.toScreenPos(it.key.toVec3dCenter())

                GL11.glTranslated(screenPos.x, screenPos.y, 0.0)
                GL11.glScalef(textScale * 2f, textScale * 2f, 1f)

                val center = FontRenderAdapter.getStringWidth(it.value.ordinal.toString()) / -2f
                FontRenderAdapter.drawString(it.value.ordinal.toString(), center, 0f, color = it.value.color)

                GL11.glPopMatrix()
            }
        }
    }

    private fun getInstrument(soundEvent: SoundEvent): NoteBlockEvent.Instrument? {
        return when (soundEvent) {
            SoundEvents.BLOCK_NOTE_HARP -> NoteBlockEvent.Instrument.PIANO
            SoundEvents.BLOCK_NOTE_BASEDRUM -> NoteBlockEvent.Instrument.BASSDRUM
            SoundEvents.BLOCK_NOTE_SNARE -> NoteBlockEvent.Instrument.SNARE
            SoundEvents.BLOCK_NOTE_HAT -> NoteBlockEvent.Instrument.CLICKS
            SoundEvents.BLOCK_NOTE_BASS -> NoteBlockEvent.Instrument.BASSGUITAR
            SoundEvents.BLOCK_NOTE_FLUTE -> NoteBlockEvent.Instrument.FLUTE
            SoundEvents.BLOCK_NOTE_BELL -> NoteBlockEvent.Instrument.BELL
            SoundEvents.BLOCK_NOTE_GUITAR -> NoteBlockEvent.Instrument.GUITAR
            SoundEvents.BLOCK_NOTE_CHIME -> NoteBlockEvent.Instrument.CHIME
            SoundEvents.BLOCK_NOTE_XYLOPHONE -> NoteBlockEvent.Instrument.XYLOPHONE
            else -> null
        }
    }
}