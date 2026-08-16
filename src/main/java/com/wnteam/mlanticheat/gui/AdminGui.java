package com.wnteam.mlanticheat.gui;

import com.wnteam.mlanticheat.config.TextConfig;
import com.wnteam.mlanticheat.data.PlayerData;
import com.wnteam.mlanticheat.data.PlayerDataManager;
import com.wnteam.mlanticheat.data.PlayerStatsStore;
import com.wnteam.mlanticheat.ml.TrainingManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public final class AdminGui implements Listener {
    private enum Sort { MAX_RISK, CURRENT_RISK, ALERTS, LAST_SEEN, NAME }
    private enum Screen { LIST, CARD, HISTORY }
    private record View(int page, Sort sort, String query, UUID inspected, boolean history) {}
    private record Entry(UUID uuid, String name, boolean online, double[] scores, double raw, long analyses, long alerts, double average, double maximum, int ping, double tps, long lastSeen, List<PlayerData.Detection> history) {}
    private static final class GuiHolder implements InventoryHolder {
        private final UUID viewer; private final Screen screen; private Inventory inventory;
        private GuiHolder(UUID viewer, Screen screen) { this.viewer = viewer; this.screen = screen; }
        public Inventory getInventory() { return inventory; }
    }

    private final JavaPlugin plugin;
    private final PlayerDataManager data;
    private final PlayerStatsStore store;
    private final TrainingManager training;
    private final NamespacedKey playerKey;
    private final NamespacedKey actionKey;
    private final Map<UUID, View> views = new HashMap<>();
    private final TextConfig gui;
    private final TextConfig messages;
    private BukkitTask refreshTask;

    public AdminGui(JavaPlugin plugin, PlayerDataManager data, PlayerStatsStore store, TrainingManager training, TextConfig gui, TextConfig messages) {
        this.plugin = plugin; this.data = data; this.store = store; this.training = training; this.gui = gui; this.messages = messages;
        playerKey = new NamespacedKey(plugin, "gui-player"); actionKey = new NamespacedKey(plugin, "gui-action"); restart();
    }

    public void reload() { gui.reload(); messages.reload(); restart(); }
    private void restart() { if (refreshTask != null) refreshTask.cancel(); long ticks = Math.max(1, gui.longValue("update-ticks", 20)); refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, ticks, ticks); }
    public void open(Player viewer) { open(viewer, ""); }
    public void open(Player viewer, String query) { views.put(viewer.getUniqueId(), new View(0, Sort.MAX_RISK, query == null ? "" : query, null, false)); renderList(viewer); }
    public void inspect(Player viewer, Player target) { inspect(viewer, target.getUniqueId()); }
    public void inspect(Player viewer, UUID uuid) { View v = views.get(viewer.getUniqueId()); views.put(viewer.getUniqueId(), new View(0, v == null ? Sort.MAX_RISK : v.sort(), v == null ? "" : v.query(), uuid, false)); renderCard(viewer); }

    private void refresh() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            View view = views.get(viewer.getUniqueId());
            if (view == null || !isOwn(viewer.getOpenInventory().getTopInventory(), viewer)) continue;
            if (view.inspected() == null) renderList(viewer); else if (view.history()) renderHistory(viewer); else renderCard(viewer);
        }
    }

    private void renderList(Player viewer) {
        View view = views.get(viewer.getUniqueId()); if (view == null) return;
        List<Entry> entries = entries(view.query()); entries.sort(comparator(view.sort()));
        List<Integer> slots = slots(gui.string("list.content-slots", "0-44"), size("list.size", 54));
        int perPage = Math.max(1, slots.size()), pages = Math.max(1, (entries.size() + perPage - 1) / perPage), page = Math.max(0, Math.min(view.page(), pages - 1));
        if (page != view.page()) { view = new View(page, view.sort(), view.query(), null, false); views.put(viewer.getUniqueId(), view); }
        Map<String, Object> vars = vars("page", page + 1, "pages", pages, "query", view.query(), "sort", view.sort().name().toLowerCase(Locale.ROOT), "search", view.query().isBlank() ? messages.string("status.all-players", "all players") : messages.plain("status.search", "search %query%", vars("query", view.query())), "seconds", gui.longValue("update-ticks", 20) / 20.0);
        Inventory inventory = create(viewer, Screen.LIST, size("list.size", 54), gui.component("list.title", "MLAC players %page%/%pages%", vars)); fill(inventory);
        int start = page * perPage;
        for (int i = start; i < Math.min(entries.size(), start + perPage); i++) inventory.setItem(slots.get(i - start), playerItem(entries.get(i)));
        if (page > 0) configured(inventory, "list.items.previous", vars, null);
        configured(inventory, "list.items.search", vars, null); configured(inventory, "list.items.sort", vars, null); configured(inventory, "list.items.realtime", vars, null);
        if (page + 1 < pages) configured(inventory, "list.items.next", vars, null); show(viewer, inventory, Screen.LIST);
    }

    private void renderCard(Player viewer) {
        View view = views.get(viewer.getUniqueId()); if (view == null) return; Entry e = entry(view.inspected()); if (e == null) { backToList(viewer, view); return; }
        Map<String, Object> base = entryVars(e); Inventory inv = create(viewer, Screen.CARD, size("card.size", 45), gui.component("card.title", "MLAC %player%", base)); fill(inv);
        List<Integer> scoreSlots = integerList("card.score-slots", List.of(10,11,12,13,14)); List<String> materials = gui.list("card.score-materials");
        for (int i = 0; i < Math.min(5, scoreSlots.size()); i++) { Map<String,Object> v = new HashMap<>(base); v.put("score_name", PlayerData.SCORE_NAMES[i]); v.put("score", format(e.scores()[i])); String material = i < materials.size() ? materials.get(i) : "STONE"; inv.setItem(scoreSlots.get(i), item(resolve(material, e.scores()[4], e.online()), gui.component("card.score-name", "%score_name% %score%", v), gui.components("card.score-lore", v), e.uuid(), "none")); }
        configured(inv, "card.items.status", base, e.uuid()); configured(inv, "card.items.statistics", base, e.uuid()); configured(inv, "card.items.risk", base, e.uuid()); configured(inv, "card.items.back", base, null);
        if (e.online()) { configured(inv, "card.items.legit", base, e.uuid()); configured(inv, "card.items.stop", base, e.uuid()); configured(inv, "card.items.cheat", base, e.uuid()); }
        show(viewer, inv, Screen.CARD);
    }

    private void renderHistory(Player viewer) {
        View view = views.get(viewer.getUniqueId()); if (view == null) return; Entry e = entry(view.inspected()); if (e == null) { backToList(viewer, view); return; }
        Map<String,Object> base = entryVars(e); Inventory inv = create(viewer, Screen.HISTORY, size("history.size",54), gui.component("history.title", "MLAC history %player%", base)); fill(inv);
        List<Integer> slots = slots(gui.string("history.content-slots", "0-44"), inv.getSize()); int index = 0;
        for (PlayerData.Detection d : e.history()) { if (index >= slots.size()) break; Map<String,Object> v = new HashMap<>(base); v.putAll(vars("rule",d.rule(),"score",format(d.score()),"raw",format(d.rawScore()),"ping",d.ping(),"tps",format(d.tps()),"seen",age(d.time()))); inv.setItem(slots.get(index++), item(riskMaterial(d.score()), gui.component("history.detection.name", "%rule% %score%", v), gui.components("history.detection.lore",v), e.uuid(), "none")); }
        configured(inv, "history.back", base, e.uuid()); show(viewer, inv, Screen.HISTORY);
    }

    private void configured(Inventory inv, String path, Map<String,Object> vars, UUID uuid) { int slot = gui.integer(path + ".slot", -1); if (slot < 0 || slot >= inv.getSize()) return; String action = gui.string(path + ".action", "none"); Material material = resolve(gui.string(path + ".material", "STONE"), number(vars.get("ml")), "online".equals(vars.get("status"))); inv.setItem(slot, item(material, gui.component(path + ".name", " ", vars), gui.components(path + ".lore", vars), uuid, action)); }
    private ItemStack playerItem(Entry e) { Map<String,Object> v = entryVars(e); return item(resolve(gui.string("list.player.material-" + riskName(Math.max(e.scores()[4], e.maximum())), "STONE"), e.scores()[4], e.online()), gui.component("list.player.name", "%player%",v), gui.components("list.player.lore",v),e.uuid(),"inspect"); }
    private Map<String,Object> entryVars(Entry e) { return vars("player",e.name(),"status",messages.string("status."+(e.online()?"online":"offline"),e.online()?"online":"offline"),"ml",format(e.scores()[4]),"raw",format(e.raw()),"max",format(e.maximum()),"average",format(e.average()),"analyses",e.analyses(),"alerts",e.alerts(),"ping",e.ping(),"tps",format(e.tps()),"seen",age(e.lastSeen()),"history",e.history().size(),"risk",riskName(e.scores()[4])); }
    private void fill(Inventory inv) { if (!gui.bool("fillers.enabled",false)) return; ItemStack filler=item(resolve(gui.string("fillers.material","GRAY_STAINED_GLASS_PANE"),0,false),gui.component("fillers.name"," ",Map.of()),gui.components("fillers.lore",Map.of()),null,"none"); for(int i=0;i<inv.getSize();i++) inv.setItem(i,filler); }
    private Inventory create(Player viewer, Screen screen, int size, Component title) { GuiHolder h=new GuiHolder(viewer.getUniqueId(),screen); h.inventory=Bukkit.createInventory(h,size,title); return h.inventory; }
    private int size(String path,int fallback) { int value=gui.integer(path,fallback); value=Math.max(9,Math.min(54,value)); return value-value%9; }
    private Material resolve(String value,double risk,boolean online) { if ("AUTO_RISK".equalsIgnoreCase(value)) return riskMaterial(risk); if ("AUTO_STATUS".equalsIgnoreCase(value)) return online?Material.LIME_DYE:Material.GRAY_DYE; Material m=Material.matchMaterial(value); return m==null?Material.STONE:m; }
    private ItemStack item(Material material,Component name,List<Component> lore,UUID uuid,String action) { ItemStack s=new ItemStack(material); ItemMeta m=s.getItemMeta(); m.displayName(name); m.lore(lore); if(uuid!=null)m.getPersistentDataContainer().set(playerKey,PersistentDataType.STRING,uuid.toString()); m.getPersistentDataContainer().set(actionKey,PersistentDataType.STRING,action); s.setItemMeta(m); return s; }
    private boolean isOwn(Inventory inv,Player p) { return inv.getHolder(false) instanceof GuiHolder h&&h.viewer.equals(p.getUniqueId()); }
    private void show(Player p,Inventory inv,Screen screen) { Inventory cur=p.getOpenInventory().getTopInventory(); if(cur.getHolder(false) instanceof GuiHolder h&&h.viewer.equals(p.getUniqueId())&&h.screen==screen&&cur.getSize()==inv.getSize()){cur.setContents(inv.getContents());p.updateInventory();}else p.openInventory(inv); }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onClick(InventoryClickEvent event) { if(!(event.getWhoClicked() instanceof Player p)||!isOwn(event.getView().getTopInventory(),p))return; event.setCancelled(true); Inventory top=event.getView().getTopInventory(); if(event.getRawSlot()<0||event.getRawSlot()>=top.getSize())return; ItemStack s=event.getCurrentItem(); if(s==null||!s.hasItemMeta())return; ItemMeta m=s.getItemMeta(); String action=m.getPersistentDataContainer().get(actionKey,PersistentDataType.STRING),raw=m.getPersistentDataContainer().get(playerKey,PersistentDataType.STRING); UUID uuid=null; try{if(raw!=null)uuid=UUID.fromString(raw);}catch(IllegalArgumentException ignored){return;} View v=views.get(p.getUniqueId()); if(v==null||action==null)return; if(action.equals("inspect")&&uuid!=null)inspect(p,uuid); else if(action.equals("history")&&uuid!=null){views.put(p.getUniqueId(),new View(0,v.sort(),v.query(),uuid,true));renderHistory(p);} else if(action.equals("card")&&uuid!=null)inspect(p,uuid); else if(action.equals("back"))backToList(p,v); else if(action.equals("prev")){views.put(p.getUniqueId(),new View(Math.max(0,v.page()-1),v.sort(),v.query(),null,false));renderList(p);} else if(action.equals("next")){views.put(p.getUniqueId(),new View(v.page()+1,v.sort(),v.query(),null,false));renderList(p);} else if(action.equals("sort")){Sort n=Sort.values()[(v.sort().ordinal()+1)%Sort.values().length];views.put(p.getUniqueId(),new View(0,n,v.query(),null,false));renderList(p);} else if(uuid!=null&&Bukkit.getPlayer(uuid)!=null){if(action.equals("legit"))training.setLabel(uuid,0);else if(action.equals("cheat"))training.setLabel(uuid,1);else if(action.equals("stop"))training.clearLabel(uuid);renderCard(p);} }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void onDrag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&isOwn(e.getView().getTopInventory(),p))e.setCancelled(true);}
    @EventHandler public void onClose(InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p)||!isOwn(e.getInventory(),p))return;Bukkit.getScheduler().runTask(plugin,()->{if(!isOwn(p.getOpenInventory().getTopInventory(),p))views.remove(p.getUniqueId());});}
    private void backToList(Player p,View v){views.put(p.getUniqueId(),new View(v.page(),v.sort(),v.query(),null,false));renderList(p);}
    private List<Entry> entries(String q){Map<UUID,Entry> out=new LinkedHashMap<>();for(PlayerStatsStore.Snapshot s:store.all())out.put(s.uuid(),from(s));for(Player p:Bukkit.getOnlinePlayers())out.put(p.getUniqueId(),live(p));String f=q.toLowerCase(Locale.ROOT);return out.values().stream().filter(e->e.name().toLowerCase(Locale.ROOT).contains(f)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));}
    private Entry entry(UUID id){if(id==null)return null;Player p=Bukkit.getPlayer(id);if(p!=null)return live(p);PlayerStatsStore.Snapshot s=store.find(id);return s==null?null:from(s);}
    private Entry live(Player p){PlayerData d=data.get(p);return new Entry(p.getUniqueId(),p.getName(),true,d.snapshotScores(),d.getRawScore(),d.getAnalyses(),d.getAlerts(),d.getCombinedAverage(),d.getCombinedMax(),p.getPing(),d.getLastTps(),d.getLastSeen(),d.detectionSnapshot());}
    private Entry from(PlayerStatsStore.Snapshot s){return new Entry(s.uuid(),s.name(),false,s.scores(),s.rawScore(),s.analyses(),s.alerts(),s.average(),s.maximum(),s.ping(),s.tps(),s.lastSeen(),s.detections());}
    private Comparator<Entry> comparator(Sort s){return switch(s){case MAX_RISK->Comparator.comparingDouble(Entry::maximum).reversed();case CURRENT_RISK->Comparator.comparingDouble((Entry e)->e.scores()[4]).reversed();case ALERTS->Comparator.comparingLong(Entry::alerts).reversed();case LAST_SEEN->Comparator.comparingLong(Entry::lastSeen).reversed();case NAME->Comparator.comparing(Entry::name,String.CASE_INSENSITIVE_ORDER);};}
    private Material riskMaterial(double r){return resolve(gui.string("list.player.material-"+riskName(r),"STONE"),0,false);}
    private String riskName(double r){double c=gui.decimal("risk.critical",.90),h=gui.decimal("risk.high",.75),m=gui.decimal("risk.medium",.50);String key=r>=c?"critical":r>=h?"high":r>=m?"medium":"low";return gui.string("risk.names."+key,key);}
    private String age(long time){if(time<=0)return messages.string("time.never","never");Duration d=Duration.ofMillis(Math.max(0,System.currentTimeMillis()-time));if(d.toDays()>0)return messages.plain("time.days","%value%d ago",vars("value",d.toDays()));if(d.toHours()>0)return messages.plain("time.hours","%value%h ago",vars("value",d.toHours()));if(d.toMinutes()>0)return messages.plain("time.minutes","%value%m ago",vars("value",d.toMinutes()));return messages.plain("time.seconds","%value%s ago",vars("value",d.toSeconds()));}
    private List<Integer> slots(String spec,int size){List<Integer> out=new ArrayList<>();for(String part:spec.split(",")){String[] range=part.trim().split("-");try{int a=Integer.parseInt(range[0]),b=range.length>1?Integer.parseInt(range[1]):a;for(int i=Math.max(0,a);i<=Math.min(size-1,b);i++)if(!out.contains(i))out.add(i);}catch(NumberFormatException ignored){}}return out;}
    private List<Integer> integerList(String path,List<Integer> fallback){List<String> raw=gui.list(path);if(raw.isEmpty())return fallback;List<Integer> out=new ArrayList<>();for(String s:raw)try{out.add(Integer.parseInt(s));}catch(NumberFormatException ignored){}return out.isEmpty()?fallback:out;}
    private static Map<String,Object> vars(Object... values){Map<String,Object> out=new HashMap<>();for(int i=0;i+1<values.length;i+=2)out.put(String.valueOf(values[i]),values[i+1]);return out;}
    private static double number(Object value){try{return Double.parseDouble(String.valueOf(value));}catch(Exception ignored){return 0;}}
    private String format(double v){return String.format(Locale.US,"%.3f",v);}
}
