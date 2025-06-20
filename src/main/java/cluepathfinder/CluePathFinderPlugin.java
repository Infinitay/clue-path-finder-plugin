package cluepathfinder;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.ClueScrollService;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;
import net.runelite.http.api.RuneLiteAPI;

@Slf4j
@PluginDescriptor(
	name = "Clue Path Finder"
)
@PluginDependency(ClueScrollPlugin.class)
public class CluePathFinderPlugin extends Plugin
{
	private static final String PLUGIN_MESSAGE_SHORTEST_PATH_NAMESPACE = "shortestpath";
	private static final String PLUGIN_MESSAGE_SHORTEST_PATH_PATH_KEY = "path";
	private static final String PLUGIN_MESSAGE_SHORTEST_PATH_CLEAR_KEY = "clear";

	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClueScrollPlugin clueScrollPlugin;

	@Inject
	private ClueScrollService clueScrollService;

	private ClueScroll clueScroll;
	private boolean wasLastPathSetByClue = false;
	private Set<WorldPoint> lastLocations = Sets.newHashSet();

	@Override
	protected void startUp() throws Exception
	{
		Optional<Plugin> shortestPathPlugin = pluginManager.getPlugins().stream().filter(plugin -> plugin.getName().equals("Shortest Path")).findAny();
		if (!pluginManager.isPluginEnabled(clueScrollPlugin))
		{
			log.warn("[#onStartUp] ClueScrollPlugin is not enabled, turning it on to ensure functionality");
			pluginManager.setPluginEnabled(clueScrollPlugin, true);
		}

		if (shortestPathPlugin.isPresent())
		{
			if (!pluginManager.isPluginEnabled(shortestPathPlugin.get()))
			{
				log.warn("[#onStartUp] ShortestPathPlugin is not enabled, turning it on to ensure functionality");
				pluginManager.setPluginEnabled(shortestPathPlugin.get(), true);
			}
		}
		else
		{
			log.warn("[#onStartUp] Clue Path Finder requires the Shortest Path plugin found on the Plugin Hub to function properly, and so this plugin has been disabled. Please ensure Shortest Path is installed and enabled before enabling this plugin.");
			// Show a message popup dialog to the user
			SwingUtilities.invokeLater(() -> {
				JOptionPane.showMessageDialog(
					client.getCanvas(),
					"Clue Path Finder requires the Shortest Path plugin found on the Plugin Hub to function properly, and so this plugin has been disabled. Please ensure Shortest Path is installed and enabled before enabling this plugin.",
					"Clue Path Finder - Plugin Dependency Missing",
					JOptionPane.WARNING_MESSAGE
				);
			});
			// Disable this plugin since the Shortest Path plugin is not available and to avoid more annoying popups
			pluginManager.setPluginEnabled(this, false);
			pluginManager.stopPlugin(this);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.clueScroll = null;
		if (this.wasLastPathSetByClue)
		{
			log.debug("[#shutDown] Resetting target location(s) on shutdown");
			this.sendShortestPathClear();
		}
		this.wasLastPathSetByClue = false;
		this.lastLocations.clear();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Keep clue scroll check in onGameTick to keep it simple when working with clues that have multiple locations and don't update the item itself (e.g. Master Clue)
		ClueScroll clueScroll = clueScrollService.getClue();
		if (clueScroll != null)
		{
			// log.debug("[Shortest-Path #Clue] Active clue scroll found");
			Set<WorldPoint> newLocationsSet = new HashSet<>();
			if (clueScroll instanceof LocationClueScroll)
			{
				// Should be a single location in an Array despite LocationClueScroll, but lets use #getLocations for consistency
				// Set<WorldPoint> ls = Set.of(((LocationClueScroll) clueScroll).getLocations(clueScrollPlugin));
				// log.debug("[Shortest-Path #Clue-Instance-Location] Clue location(s) ({}): {}", ls.size(), ls);
				newLocationsSet = Sets.newHashSet(((LocationClueScroll) clueScroll).getLocations(clueScrollPlugin));
			}

			if (clueScroll instanceof LocationsClueScroll)
			{
				// Set<WorldPoint> ls = Set.of(((LocationsClueScroll) clueScroll).getLocations(clueScrollPlugin));
				// log.debug("[Shortest-Path #Clue-Instance-Location] Clue location(s) ({}): {}", ls.size(), ls);
				newLocationsSet = Sets.newHashSet(((LocationsClueScroll) clueScroll).getLocations(clueScrollPlugin));
			}

			// newLocationsSet = Sets.newHashSet(new WorldPoint(2288, 4702, 0));

			/*log.debug("[Shortest-Path #Clue-Instance-Location] Clue location(s) ({}): {}", newLocationsSet.size(), newLocationsSet.stream()
				.map(WorldPoint::toString)
				.collect(Collectors.joining(", ")));*/

			if (newLocationsSet.size() != this.lastLocations.size() || !Sets.difference(newLocationsSet, this.lastLocations).isEmpty())
			{
				log.debug("[#onGameTick] Clue location(s) changed: {} -> {}", this.lastLocations, newLocationsSet);
				this.lastLocations = newLocationsSet;

				if (!newLocationsSet.isEmpty())
				{
					this.sendShortestPathDestinations(newLocationsSet);
					this.wasLastPathSetByClue = true;
				}
				else
				{
					log.debug("[#onGameTick] No locations found in clue scroll");
					if (this.wasLastPathSetByClue)
					{
						log.debug("[#onGameTick] Resetting target location(s) due to empty clue");
						this.sendShortestPathClear();
						this.wasLastPathSetByClue = false;
					}
				}
			}

			this.clueScroll = clueScroll;
		}
		else
		{
			// There's no active clue scroll, so lets check to see if we need to reset the pathfinder
			// log.debug("[Shortest-Path #Clue] No active clue scroll (NULL)");
			if (this.wasLastPathSetByClue)
			{
				log.debug("[#onGameTick] Resetting target location(s) due to null clue");
				this.sendShortestPathClear();
				this.wasLastPathSetByClue = false;
				this.lastLocations.clear();
			}
			this.clueScroll = null;
		}
	}

	private Map<String, Object> generatePathPayload(WorldPoint startingWorldPoint, WorldPoint destinationWorldPoint)
	{
		return Map.of(
			"start", startingWorldPoint,
			"target", destinationWorldPoint
		);
	}

	private Map<String, Object> generatePathPayload(WorldPoint destinationWorldPoint)
	{
		WorldPoint startingWorldPoint = client.getLocalPlayer().getWorldLocation();
		if (startingWorldPoint == null)
		{
			log.warn("[#generatePathPayload-WorldPoint] Player's starting world point is null, cannot generate payload starting point");
			return Map.of();
		}
		return this.generatePathPayload(startingWorldPoint, destinationWorldPoint);
	}

	private Map<String, Object> generatePathPayload(WorldPoint startingWorldPoint, Set<WorldPoint> destinationWorldPointSet)
	{
		return Map.of(
			"start", startingWorldPoint,
			"target", destinationWorldPointSet
		);
	}

	private Map<String, Object> generatePathPayload(Set<WorldPoint> destinationWorldPointSet)
	{
		WorldPoint startingWorldPoint = client.getLocalPlayer().getWorldLocation();
		if (startingWorldPoint == null)
		{
			log.warn("[#generatePathPayload-Set] Player's starting world point is null, cannot generate payload starting point");
			return Map.of();
		}
		return this.generatePathPayload(startingWorldPoint, destinationWorldPointSet);
	}

	private Map<String, Object> generatePathPayload(WorldPoint... destinationWorldPoints)
	{
		if (destinationWorldPoints == null || destinationWorldPoints.length == 0)
		{
			log.warn("[#generatePathPayload-WorldPoints...] No destination world points provided for path generation");
			return Map.of();
		}

		return this.generatePathPayload(Sets.newHashSet(destinationWorldPoints));
	}

	private boolean sendShortestPathDestinations(Set<WorldPoint> destinationWorldPointSet)
	{
		if (destinationWorldPointSet == null || destinationWorldPointSet.isEmpty())
		{
			log.warn("[#sendShortestPathDestinations] The destination set is empty or null so we can't set a path");
			return false;
		}

		Map<String, Object> payload = this.generatePathPayload(destinationWorldPointSet);
		if (payload.isEmpty())
		{
			log.warn("[#sendShortestPathDestinations] Failed to generate payload for path generation");
			return false;
		}

		this.sendShortestPathClear();
		this.eventBus.post(new PluginMessage(PLUGIN_MESSAGE_SHORTEST_PATH_NAMESPACE, PLUGIN_MESSAGE_SHORTEST_PATH_PATH_KEY, payload));
		return true;
	}

	private void sendShortestPathClear()
	{
		this.eventBus.post(new PluginMessage(PLUGIN_MESSAGE_SHORTEST_PATH_NAMESPACE, PLUGIN_MESSAGE_SHORTEST_PATH_CLEAR_KEY));
	}
}
