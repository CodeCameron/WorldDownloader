package wdl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemMap;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.network.play.server.S34PacketMaps;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapData;
import wdl.api.IBlockEventListener;
import wdl.api.IChatMessageListener;
import wdl.api.IGuiHooksListener;
import wdl.api.IPluginChannelListener;
import wdl.api.IWDLMod;

/**
 * The various hooks for WDL. <br/>
 * All of these should be called regardless of any WDL state variables.
 */
public class WDLHooks {
	private static final Profiler profiler = Minecraft.getMinecraft().mcProfiler;
	
	/**
	 * All WDLMods that implement {@link IGuiHooksListener}.
	 */
	public static Map<String, IGuiHooksListener> guiListeners =
			new HashMap<String, IGuiHooksListener>();
	/**
	 * All WDLMods that implement {@link IChatMessageListener}.
	 */
	public static Map<String, IChatMessageListener> chatMessageListeners =
			new HashMap<String, IChatMessageListener>();
	/**
	 * All WDLMods that implement {@link IPluginChannelListener}.
	 */
	public static Map<String, IPluginChannelListener> pluginChannelListeners =
			new HashMap<String, IPluginChannelListener>();
	/**
	 * All WDLMods that implement {@link IBlockEventListener}.
	 */
	public static Map<String, IBlockEventListener> blockEventListeners =
			new HashMap<String, IBlockEventListener>();
	
	/**
	 * Called when {@link WorldClient#tick()} is called.
	 * <br/>
	 * Should be at end of the method.
	 */
	public static void onWorldClientTick(WorldClient sender) {
		try {
			profiler.startSection("wdl");
			
			if (sender != WDL.worldClient) {
				profiler.startSection("onWorldLoad");
				if (WDL.worldLoadingDeferred) {
					return;
				}
				
				WDLEvents.onWorldLoad(sender);
				profiler.endSection();
			} else {
				profiler.startSection("inventoryCheck");
				if (WDL.downloading && WDL.thePlayer != null) {
					if (WDL.thePlayer.openContainer != WDL.windowContainer) {
						if (WDL.thePlayer.openContainer == WDL.thePlayer.inventoryContainer) {
							boolean handled;
							
							profiler.startSection("onItemGuiClosed");
							profiler.startSection("Core");
							handled = WDLEvents.onItemGuiClosed();
							profiler.endSection();
							
							Container container = WDL.thePlayer.openContainer;
							if (WDL.lastEntity != null) {
								Entity entity = WDL.lastEntity;

								for (Map.Entry<String, IGuiHooksListener> e :
										guiListeners.entrySet()) {
									if (handled) {
										break;
									}

									profiler.startSection(e.getKey());
									handled = e.getValue().onEntityGuiClosed(
											sender, entity, container);
									profiler.endSection();
								}
								
								if (!handled) {
									WDL.chatDebug(WDLMessageTypes.
											ON_GUI_CLOSED_WARNING,
											"onItemGuiClosed: Unrecognised " +
											"entity could not be saved: " + 
											EntityUtils.getEntityType(entity));
								}
							} else {
								BlockPos pos = WDL.lastClickedBlock;
								for (Map.Entry<String, IGuiHooksListener> e :
										guiListeners.entrySet()) {
									if (handled) {
										break;
									}

									profiler.startSection(e.getKey());
									handled = e.getValue().onBlockGuiClosed(
											sender, pos, container);
									profiler.endSection();
								}
								
								if (!handled) {
									WDL.chatDebug(WDLMessageTypes.
											ON_GUI_CLOSED_WARNING,
											"onItemGuiClosed: unhandled TE @" + 
											pos + ": " + sender.getTileEntity(pos));
								}
							}
							
							profiler.endSection();
						} else {
							profiler.startSection("onItemGuiOpened");
							profiler.startSection("Core");
							WDLEvents.onItemGuiOpened();
							profiler.endSection();
							profiler.endSection();
						}
	
						WDL.windowContainer = WDL.thePlayer.openContainer;
					}
				}
				profiler.endSection();
			}
			
			profiler.endSection();
		} catch (Throwable e) {
			WDL.minecraft.crashed(CrashReport.makeCrashReport(e,
					"WDL mod: exception in onWorldClientTick event"));
		}
	}

	/**
	 * Called when {@link WorldClient#doPreChunk(int, int, boolean)} is called.
	 * <br/>
	 * Should be at the start of the method.
	 */
	public static void onWorldClientDoPreChunk(WorldClient sender, int x,
			int z, boolean loading) {
		try {
			if (!WDL.downloading) { return; }
			
			profiler.startSection("wdl");
			
			if (!loading) {
				profiler.startSection("onChunkNoLongerNeeded");
				Chunk c = sender.getChunkFromChunkCoords(x, z); 
				
				profiler.startSection("Core");
				wdl.WDLEvents.onChunkNoLongerNeeded(c);
				profiler.endSection();
				
				profiler.endSection();
			}
			
			profiler.endSection();
		} catch (Throwable e) {
			WDL.minecraft.crashed(CrashReport.makeCrashReport(e,
					"WDL mod: exception in onWorldDoPreChunk event"));
		}
	}

	/**
	 * Called when {@link WorldClient#removeEntityFromWorld(int)} is called.
	 * <br/>
	 * Should be at the start of the method.
	 * 
	 * @param eid
	 *            The entity's unique ID.
	 */
	public static void onWorldClientRemoveEntityFromWorld(WorldClient sender,
			int eid) {
		try {
			if (!WDL.downloading) { return; }
			
			profiler.startSection("wdl.onRemoveEntityFromWorld");
			
			Entity entity = sender.getEntityByID(eid);
			
			profiler.startSection("Core");
			WDLEvents.onRemoveEntityFromWorld(entity);
			profiler.endSection();
			
			profiler.endSection();
		} catch (Throwable e) {
			WDL.minecraft.crashed(CrashReport.makeCrashReport(e,
					"WDL mod: exception in onWorldRemoveEntityFromWorld event"));
		}
	}

	/**
	 * Called when {@link NetHandlerPlayClient#handleChat(S02PacketChat)} is
	 * called.
	 * <br/>
	 * Should be at the end of the method.
	 */
	public static void onNHPCHandleChat(NetHandlerPlayClient sender,
			S02PacketChat packet) {
		try {
			if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
				return;
			}
			
			if (!WDL.downloading) { return; }
			
			profiler.startSection("wdl.onChatMessage");
			
			//func_148915_c returns the IChatComponent.
			String chatMessage = packet.func_148915_c().getFormattedText();
			
			profiler.startSection("Core");
			WDLEvents.onChatMessage(chatMessage);
			profiler.endSection();
			
			for (Map.Entry<String, IChatMessageListener> e : 
					chatMessageListeners.entrySet()) {
				profiler.startSection(e.getKey());
				e.getValue().onChat(WDL.worldClient, chatMessage);
				profiler.endSection();
			}
			
			profiler.endSection();
		} catch (Throwable e) {
			WDL.minecraft.crashed(CrashReport.makeCrashReport(e,
					"WDL mod: exception in onNHPCHandleChat event"));
		}
	}

	/**
	 * Called when {@link NetHandlerPlayClient#handleMaps(S34PacketMaps)} is
	 * called.
	 * <br/>
	 * Should be at the end of the method.
	 */
	public static void onNHPCHandleMaps(NetHandlerPlayClient sender,
			S34PacketMaps packet) {
		try {
			if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
				return;
			}
			
			if (!WDL.downloading) { return; }
			
			profiler.startSection("wdl.onMapDataLoaded");
			
			int id = packet.getMapId();
			MapData mapData = ItemMap.loadMapData(packet.getMapId(),
					WDL.worldClient);
			
			profiler.startSection("Core");
			WDLEvents.onMapDataLoaded(id, mapData);
			profiler.endSection();
			
			profiler.endSection();
		} catch (Throwable e) {
			WDL.minecraft.crashed(CrashReport.makeCrashReport(e,
					"WDL mod: exception in onNHPCHandleMaps event"));
		}
	}

	/**
	 * Called when
	 * {@link NetHandlerPlayClient#handleCustomPayload(S3FPacketCustomPayload)}
	 * is called.
	 * <br/>
	 * Should be at the end of the method.
	 */
	public static void onNHPCHandleCustomPayload(NetHandlerPlayClient sender,
			S3FPacketCustomPayload packet) {
		try {
			if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
				return;
			}
						
			String channel = packet.getChannelName();
			byte[] payload = packet.getBufferData().array();
			
			profiler.startSection("Core");
			WDLEvents.onPluginChannelPacket(channel, payload);
			profiler.endSection();
			
			for (Map.Entry<String, IPluginChannelListener> e : 
					pluginChannelListeners.entrySet()) {
				profiler.startSection(e.getKey());
				e.getValue().onPluginChannelPacket(WDL.worldClient, channel,
						payload);
				profiler.endSection();
			}
			
			profiler.endSection();
		} catch (Throwable e) {
			WDL.minecraft.crashed(CrashReport.makeCrashReport(e,
					"WDL mod: exception in onNHPCHandleCustomPayload event"));
		}
	}

	/**
	 * Called when
	 * {@link NetHandlerPlayClient#handleBlockAction(S24PacketBlockAction)} is
	 * called.
	 * <br/>
	 * Should be at the end of the method.
	 */
	public static void onNHPCHandleBlockAction(NetHandlerPlayClient sender,
			S24PacketBlockAction packet) {
		try {
			if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
				return;
			}
			
			if (!WDL.downloading) { return; }
			
			profiler.startSection("wdl.onBlockEvent");
			
			BlockPos pos = packet.func_179825_a();
			Block block = packet.getBlockType();
			int data1 = packet.getData1();
			int data2 = packet.getData2();
			
			profiler.startSection("Core");
			WDLEvents.onBlockEvent(pos, block, data1, data2);
			profiler.endSection();
			
			for (Map.Entry<String, IBlockEventListener> e : 
					blockEventListeners.entrySet()) {
				profiler.startSection(e.getKey());
				e.getValue().onBlockEvent(WDL.worldClient, pos, block, 
						data1, data2);
				profiler.endSection();
			}
			
			profiler.endSection();
		} catch (Throwable e) {
			WDL.minecraft.crashed(CrashReport.makeCrashReport(e,
					"WDL mod: exception in onNHPCHandleBlockAction event"));
		}
	}
	
	/**
	 * Injects WDL information into a crash report.
	 * 
	 * Called at the end of {@link CrashReport#populateEnvironment()}.
	 * @param report
	 */
	public static void onCrashReportPopulateEnvironment(CrashReport report) {
		report.makeCategory("World Downloader Mod").addCrashSectionCallable("Info",
			new Callable() {
				public String call() {
					return WDL.getDebugInfo();
				}
			});
	}
}
