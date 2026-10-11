/*
 * StrafeRules - turns off Strafe Client modules a server does not allow.
 * Copyright (C) 2026 Creeperdiamonds Studios
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see
 * <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.creeperdiamonds.straferules;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tells players on Strafe Client which built-in modules must stay off here. The client asks by registering the
 * {@code strafe:rules} channel when it joins; anyone else never gets a message. Only plain Bukkit API, so it runs
 * on every server from 1.8 to 26.x.
 */
public final class StrafeRulesPlugin extends JavaPlugin implements Listener {

    public static final String CHANNEL = "strafe:rules";
    private static final int PROTOCOL = 1;
    /** Every module ID in Strafe Client (keep in sync with the client and strafeclient.com/modules). */
    static final Set<String> MODULES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "fps", "cps", "keystrokes", "armorstatus", "potioneffects", "coordinates", "direction", "ping", "time",
            "systemresources", "serveraddress", "speedmeter", "playtime",
            "zoom", "perspective", "brightness", "togglesprint",
            "lowfire", "skinnedtotem", "tinytotem")));

    @Override
    public void onEnable() {
        saveDefaultConfig();
        warnUnknown();
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    /** The client registers the channel right after joining: that's when it can receive the rules. */
    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        if (CHANNEL.equals(event.getChannel()) && event.getPlayer().isOnline()) {
            send(event.getPlayer());
        }
    }

    /** Newer clients register during login, before they can receive anything: send once they're in. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && hasStrafe(player)) {
                send(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (hasStrafe(event.getPlayer())) {
            send(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            reloadConfig();
            warnUnknown();
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasStrafe(player)) {
                    send(player);
                    count++;
                }
            }
            sender.sendMessage(ChatColor.GREEN + "StrafeRules reloaded; sent the new rules to " + count + " Strafe player" + (count == 1 ? "" : "s") + ".");
            return true;
        }
        if (sub.equals("list")) {
            List<String> global = getConfig().getStringList("blocked-modules");
            sender.sendMessage(ChatColor.GOLD + "Blocked everywhere: " + ChatColor.WHITE + (global.isEmpty() ? "nothing" : String.join(", ", global)));
            ConfigurationSection worlds = getConfig().getConfigurationSection("worlds");
            if (worlds != null) {
                for (String world : worlds.getKeys(false)) {
                    sender.sendMessage(ChatColor.GOLD + "Also in " + world + ": " + ChatColor.WHITE + String.join(", ", worlds.getStringList(world)));
                }
            }
            int online = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (hasStrafe(player)) {
                    online++;
                }
            }
            sender.sendMessage(ChatColor.GRAY + "Players on Strafe right now: " + online);
            return true;
        }
        return false;
    }

    private static boolean hasStrafe(Player player) {
        return player.getListeningPluginChannels().contains(CHANNEL);
    }

    /** The modules this player must keep off in their current world. */
    private Set<String> blockedFor(Player player) {
        Set<String> blocked = new LinkedHashSet<String>();
        if (player.hasPermission("straferules.bypass")) {
            return blocked;
        }
        for (String slug : getConfig().getStringList("blocked-modules")) {
            blocked.add(slug.toLowerCase(Locale.ROOT));
        }
        ConfigurationSection worlds = getConfig().getConfigurationSection("worlds");
        if (worlds != null && worlds.isList(player.getWorld().getName())) {
            for (String slug : worlds.getStringList(player.getWorld().getName())) {
                blocked.add(slug.toLowerCase(Locale.ROOT));
            }
        }
        // straferules.bypass.<id> keeps just that module on
        blocked.removeIf(slug -> player.hasPermission("straferules.bypass." + slug));
        return blocked;
    }

    /** Wire format (same as the client and Basalt): varint protocol, bool trusted, varint count, string per slug. */
    private void send(Player player) {
        Set<String> blocked = blockedFor(player);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, PROTOCOL);
        out.write(0); // trusted: StrafeRules doesn't check mods
        writeVarInt(out, blocked.size());
        for (String slug : blocked) {
            byte[] bytes = slug.getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, bytes.length);
            out.write(bytes, 0, bytes.length);
        }
        player.sendPluginMessage(this, CHANNEL, out.toByteArray());
    }

    private void warnUnknown() {
        Set<String> all = new LinkedHashSet<String>(getConfig().getStringList("blocked-modules"));
        ConfigurationSection worlds = getConfig().getConfigurationSection("worlds");
        if (worlds != null) {
            for (String world : worlds.getKeys(false)) {
                all.addAll(worlds.getStringList(world));
            }
        }
        for (String slug : all) {
            if (!MODULES.contains(slug.toLowerCase(Locale.ROOT))) {
                getLogger().warning("Unknown Strafe module ID '" + slug + "' (see https://strafeclient.com/modules)");
            }
        }
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
