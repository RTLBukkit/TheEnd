package plugin.bleachisback.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.PortalType;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCreatePortalEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class EndMain extends JavaPlugin implements Listener
{
	private Logger log;
	private Server server;
	private PluginDescriptionFile desc;
	private PluginManager pm;
	private Configuration config;
	
	private EndStage stage=EndStage.BEFORE;
	private World end=null;
	private ArrayList<OfflinePlayer> enders= new ArrayList<OfflinePlayer>();
	
	private static final int PORTAL_TIMING=203;
			
	public void onEnable()
	{
		server=getServer();
		log=getLogger();
		desc=getDescription();
		pm=server.getPluginManager();
		
		pm.registerEvents(this, this);
		
		setupConfig();
		
		trace(desc.getFullName()+" is enabled");
	}
	
	public void onDisable()
	{
		saveConfig();
		trace(desc.getName()+" is disabled");
	}
	
	private void trace(String string)
	{
		log.info(string);
	}
	
	private void setupConfig()
	{
		saveDefaultConfig();
		config=getConfig();
		
		stage=EndStage.fromName(config.getString("stage", EndStage.BEFORE.getName()));
		if(stage==EndStage.DURING_ONE)
		{
			end=server.getWorld(UUID.fromString(config.getString("world")));
			for(String name:config.getStringList("players"))
			{
				enders.add(server.getOfflinePlayer(name));
			}
		}
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
	{
		stage=EndStage.BEFORE;
		config.set("stage", EndStage.BEFORE.getName());
		config.set("crystals", null);
		config.set("players", null);
		config.set("world", null);
		config.set("portal-loc-x", null);
		config.set("portal-loc-z", null);
		saveConfig();
		sender.sendMessage(ChatColor.GREEN+"The End has been reset.");
		return true;
	}
	
	//Used for testing
	/*@EventHandler
	public void onDragonDamage(EntityDamageEvent e)
	{
		if(e.getEntityType()==EntityType.ENDER_DRAGON)
		{
			e.setDamage(200);
		}
	}*/
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onDragonDeath(EntityDeathEvent e)
	{
		if(stage==EndStage.BEFORE)
		{
			if(e.getEntityType()==EntityType.ENDER_DRAGON)
			{
				final Location loc=e.getEntity().getLocation();
				server.getScheduler().scheduleSyncDelayedTask(this, new Runnable()
				{
					public void run()
					{
						advanceStage(loc);
					}
				}, PORTAL_TIMING);
			}
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerRespawn(PlayerRespawnEvent e)
	{
		if(enders.contains(e.getPlayer())&&(stage==EndStage.DURING_ONE||stage==EndStage.DURING_TWO))
		{
			Location spawnLoc=new Location(end,100,50,0);
			e.setRespawnLocation(spawnLoc);
			e.getPlayer().sendMessage(ChatColor.DARK_RED+"Death is no escape");
		}
	}
	
	@SuppressWarnings("incomplete-switch")
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	public void onPVP(EntityDamageByEntityEvent e)
	{
		if(e.getEntity() instanceof Player&&e.getDamager() instanceof Player&&stage==EndStage.DURING_ONE)
		{
			if(e.getEntity().getWorld()==end)
			{
				e.setCancelled(false);
			}
		}
		else if(e.getEntityType()==EntityType.ENDER_CRYSTAL)
		{
			switch(stage)
			{
				case BEFORE:
					Location loc=e.getEntity().getLocation();
					Integer[] locArray={loc.getBlockX(),loc.getBlockY(),loc.getBlockZ()};
					if(!config.contains("crystals"))
					{
						ArrayList<Integer[]> locList=new ArrayList<Integer[]>();
						locList.add(locArray);
						config.set("crystals", locList);
					}
					else
					{
						@SuppressWarnings("unchecked")
						ArrayList<Integer[]> locList=(ArrayList<Integer[]>) config.getList("crystals");
						locList.add(locArray);
						config.set("crystals", locList);
					}
					saveConfig();
					break;
				case DURING_ONE:
				case DURING_TWO:
					e.getEntity().getWorld().spawn(e.getEntity().getLocation(), Wither.class);
					break;
			}			
		}
	}
	
	@EventHandler(priority=EventPriority.MONITOR)	
	public void onPlayerPortal(PlayerPortalEvent e)
	{
		if(e.getCause()==TeleportCause.END_PORTAL)
		{
			switch(stage)
			{				
				case BEFORE:
					e.getPlayer().sendMessage(ChatColor.DARK_GRAY+"Welcome to my domain, mortal");
					break;
				case DURING_ONE:
				case DURING_TWO:
					e.getPlayer().sendMessage(ChatColor.DARK_RED+"Welcome to die!");
					enders.add(e.getPlayer());
					if(!config.contains("players"))
					{
						ArrayList<String> playerList=new ArrayList<String>();
						playerList.add(e.getPlayer().getName());
						config.set("players", playerList);
					}
					else
					{
						List<String> playerList=config.getStringList("players");
						playerList.add(e.getPlayer().getName());
						config.set("players", playerList);
					}
					saveConfig();
					break;
				case COMPLETED:
					if(enders.contains(e.getPlayer()))
					{
						e.getPlayer().getInventory().addItem(new ItemStack(Material.DRAGON_EGG));
						enders.remove(e.getPlayer());
						List<String> nameList=config.getStringList("players");
						nameList.remove(e.getPlayer().getName());
						config.set("players", nameList);
						saveConfig();
					}
					break;
			}
		}
	}
	
	@SuppressWarnings("incomplete-switch")
	@EventHandler(priority=EventPriority.HIGH)
	public void onBlockInteract(PlayerInteractEvent e)
	{
		if(e.getAction()==Action.LEFT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_AIR)
		{
			return;
		}
		if(e.getClickedBlock().getType()==Material.DRAGON_EGG&&e.getClickedBlock().getWorld()==end)
		{
			switch(stage)
			{
				case DURING_ONE:
					advanceStage(e.getClickedBlock().getLocation());
					e.getClickedBlock().setType(Material.AIR);
					e.setCancelled(true);
					break;
				case DURING_TWO:
					e.setCancelled(true);
					break;
			}
		}
	}
	
	@SuppressWarnings("incomplete-switch")
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onPlayerCommand(PlayerCommandPreprocessEvent e)
	{
		if(enders.contains(e.getPlayer()))
		{
			switch(stage)
			{
				case DURING_ONE:
				case DURING_TWO:
				case COMPLETED:
					e.getPlayer().sendMessage(ChatColor.DARK_RED+"You hold no power in this realm, mortal");
					e.setCancelled(true);					
					break;
			}			
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onTargetSwitch(EntityTargetLivingEntityEvent e)
	{
		if(e.getEntityType()==EntityType.WITHER&&(stage==EndStage.DURING_ONE||stage==EndStage.DURING_TWO))
		{
			if(e.getEntity().getWorld()==end&&e.getTarget().getType()!=EntityType.PLAYER)
			{
				//I know the code always gets here, but the withers keep attacking other stuff anyway.
				e.setCancelled(true);
				((Creature) e.getEntity()).setTarget(null);
			}
		}
	}

	@SuppressWarnings("incomplete-switch")
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onPortalCreate(EntityCreatePortalEvent e)
	{
		if(e.getPortalType()==PortalType.ENDER&&e.getEntityType()==EntityType.ENDER_DRAGON)
		{
			switch(stage)
			{
				case DURING_TWO:
					e.setCancelled(true);
					if(end.getEntitiesByClass(EnderDragon.class).size()<=1)
					{
						advanceStage(null);
					}
					break;
			}
		}
	}
	
	@SuppressWarnings("incomplete-switch")
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e)
	{
		switch(stage)
		{
			case DURING_ONE:
			case DURING_TWO:
			case COMPLETED:
				if(config.getStringList("players").contains(e.getPlayer().getName()))
				{
					enders.add(e.getPlayer());
				}
				break;
		}
	}
	
	@SuppressWarnings({ "incomplete-switch", "unchecked" })
	private void advanceStage(Location loc)
	{
		switch(stage)
		{
			case BEFORE: 
				stage=EndStage.DURING_ONE;
				config.set("stage", stage.getName());
				end=loc.getWorld();
				config.set("world", end.getUID().toString());
				config.set("portal-loc-x", loc.getBlockX());
				config.set("portal-loc-z", loc.getBlockZ());
				loc.getWorld().setPVP(true);
				
				for(int x=-5;x<6;x++)
				{
					for(int z=-5;z<6;z++)
					{
						if(loc.getWorld().getBlockAt(loc.getBlockX()+x,64,loc.getBlockZ()+z).getType()==Material.ENDER_PORTAL)
						{
							loc.getWorld().getBlockAt(loc.getBlockX()+x,64,loc.getBlockZ()+z).setType(Material.OBSIDIAN);
						}
					}
				}
				
				for(Player player:end.getPlayers())
				{
					enders.add(player);
					if(!config.contains("players"))
					{
						ArrayList<String> playerList=new ArrayList<String>();
						playerList.add(player.getName());
						config.set("players", playerList);
					}
					else
					{
						List<String> playerList=config.getStringList("players");
						playerList.add(player.getName());
						config.set("players", playerList);
					}
					player.sendMessage(ChatColor.DARK_RED+"You think this is The End?");
				}
				saveConfig();
				break;
			case DURING_ONE:
				for(Player player:end.getPlayers())
				{
					player.sendMessage(ChatColor.DARK_RED+"The End starts now!");
				}
				stage=EndStage.DURING_TWO;
				//Creates 10 eggs and pillars to spawn dragons
				loop: for(int i=0;i<5;i++)
				{
					final int x=loc.getBlockX()+(new Random()).nextInt(129)-64;
					final int z=loc.getBlockZ()+(new Random()).nextInt(129)-64;
					if(loc.getWorld().getHighestBlockAt(x, z).getRelative(BlockFace.DOWN).getType()==Material.ENDER_STONE)
					{						
						final int highestY=loc.getWorld().getHighestBlockYAt(x, z)-1;
						//checks to make sure that the obsidian pillars don't overwrite anything
						//each pillar is 32 meters tall and has a 4 meter radius
						for(int y=highestY+1;y<highestY+32;y++)
						{
							for(int $x=x-4;$x<x+5;$x++)
							{
								for(int $z=z-4;$z<z+5;$z++)
								{
									Block block=loc.getWorld().getBlockAt(x+$x, y, z+$z);
									if(block.getType()!=Material.AIR&&block.getType()!=Material.ENDER_STONE)
									{
										i--;
										continue loop;
									}
								}
							}
						}
						loc.getWorld().getBlockAt(x, highestY+1, z).setType(Material.DRAGON_EGG);
						final int id=server.getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
						{
							int currentY=highestY+1;
							public void run()
							{
								for(int $x=x-4;$x<x+5;$x++)
								{
									for(int $z=z-4;$z<z+5;$z++)
									{
										Location location=new Location(end,$x,currentY,$z);
										if(location.distance(new Location(end,x,currentY,z))<=4.25)
										{
											location.getBlock().setType(Material.OBSIDIAN);
										}
									}
								}
								end.getBlockAt(x, currentY+1, z).setType(Material.DRAGON_EGG);
								currentY++;
							}
						}, 200, 10);
						server.getScheduler().scheduleSyncDelayedTask(this, new Runnable()
						{
							public void run()
							{
								server.getScheduler().cancelTask(id);
							}
						}, 310+200+1);
						server.getScheduler().scheduleSyncDelayedTask(this, new Runnable()
						{
							public void run()
							{
								end.spawn(new Location(end, x, highestY+43, z), EnderDragon.class);
								end.spawn(new Location(end, x+.5, highestY+33, z+.5), EnderCrystal.class);
								end.getBlockAt(x, highestY+33, z).setType(Material.BEDROCK);
								end.getBlockAt(x, highestY+34, z).setType(Material.FIRE);
							}
						}, 310+200+200);
					}
					else
					{
						i--;
						continue loop;
					}
				}
				//Respawns all of the missing crystals
				if(config.contains("crystals"))
				{
					for(Integer[] crystalLocArray:((List<Integer[]>)config.get("crystals")))
					{
						end.getBlockAt(crystalLocArray[0], crystalLocArray[1]-1, crystalLocArray[2]).setType(Material.BEDROCK);
						end.getBlockAt(crystalLocArray[0], crystalLocArray[1], crystalLocArray[2]).setType(Material.FIRE);						
						end.spawn(new Location(end, crystalLocArray[0]+.5, crystalLocArray[1]-1, crystalLocArray[2]+.5), EnderCrystal.class);
					}
				}				
				break;
			case DURING_TWO:
				stage=EndStage.COMPLETED;
				int x=config.getInt("portal-loc-x");
				int y=64;
				int z=config.getInt("portal-loc-z");
				loopx: for(int $x=x-7;$x<x+7;$x++)
				{
					for(int $z=z-7;$z<z+7;$z++)
					{						
						if((end.getBlockAt($x, y, $z).getType()==Material.BEDROCK)&&(end.getBlockAt($x, y+1, $z).getType()==Material.BEDROCK))
						{
							x=$x;
							z=$z;
							break loopx;
						}
					}
				}
				for(int $x=x-4;$x<x+4;$x++)
				{
					for(int $z=z-4;$z<z+4;$z++)
					{
						Location blockLoc=new Location(end,$x,y,$z);
						if(blockLoc.getBlock().getType()!=Material.BEDROCK&&blockLoc.distance(new Location(end,x,y,z))<3)
						{
							blockLoc.getBlock().setTypeId(Material.ENDER_PORTAL.getId(),false);
							blockLoc.getBlock().getState().update();
						}
					}
				}
				break;
		}		
	}
}
