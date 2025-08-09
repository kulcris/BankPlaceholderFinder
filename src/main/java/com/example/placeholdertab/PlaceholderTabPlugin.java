package com.example.placeholdertab;

import com.google.inject.Provides;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.util.HotkeyListener;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

@Slf4j
@PluginDescriptor(
    name = "Placeholder Tab",
    description = "Hotkey + clickable tab to filter bank to only placeholders",
    tags = {"bank", "placeholder", "hotkey", "tab", "search"}
)
public class PlaceholderTabPlugin extends Plugin
{
    // --- Injected ---
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private PlaceholderTabConfig config;
    @Inject private ItemManager itemManager;
    @Inject private BankSearch bankSearch;
    @Inject private KeyManager keyManager;
    @Inject private PlaceholderSearch placeholderSearch;

    // --- Numeric widget IDs to avoid ComponentID drift ---
    private static final int BANK_GROUP_ID = 12;
    private static final int BANK_ROOT = (BANK_GROUP_ID << 16) | 1;
    private static final int BANK_TAB_CONTAINER = (BANK_GROUP_ID << 16) | 65; // tag/tabs row

    // UI state
    private boolean active = false;
    private boolean pendingApply = false;
    private boolean pendingClear = false;

    // Our faux tab widget id & label
    private int tabWidgetId = -1;

    private final HotkeyListener toggleHotkey = new HotkeyListener(() -> config.toggleHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            active = !active;
            if (active) { pendingApply = true; pendingClear = false; }
            else { pendingClear = true; pendingApply = false; }
        }
    };

    @Provides
    PlaceholderTabConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PlaceholderTabConfig.class);
    }

    @Override
    protected void startUp()
    {
        registerBankPredicate(placeholderSearch.predicateToken(), placeholderSearch::itemIsPlaceholder);
        keyManager.registerKeyListener(toggleHotkey);
        tabWidgetId = -1;
        active = false;
        pendingApply = false;
        pendingClear = false;
    }

    @Override
    protected void shutDown()
    {
        keyManager.unregisterKeyListener(toggleHotkey);
        unregisterBankPredicate(placeholderSearch.predicateToken());
        clientThread.invokeLater(placeholderSearch::clearSearchIfOurs);
        removeTab();
        active = false;
        pendingApply = false;
        pendingClear = false;
    }

    // Add/update our tab when bank loads and every tick while bank is open (cheap)
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (client.getWidget(BANK_ROOT) == null)
        {
            tabWidgetId = -1; // bank closed
            return;
        }

        addOrUpdateTab();

        if (pendingApply)
        {
            pendingApply = false;
            clientThread.invokeLater(() -> {
                if (!setBankSearchText("is:placeholder"))
                {
                    boolean ok = placeholderSearch.applySearchSafe();
                    if (!ok) active = false;
                }
            });
        }
        else if (pendingClear)
        {
            pendingClear = false;
            clientThread.invokeLater(() -> {
                if (!setBankSearchText(""))
                {
                    placeholderSearch.clearSearchIfOurs();
                }
            });
        }
    }

    // Handle clicks on our faux tab via menu system
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e)
    {
        if (e.getWidgetId() == tabWidgetId && "View".equals(e.getMenuOption()))
        {
            e.consume();
            active = !active;
            if (active) { pendingApply = true; pendingClear = false; }
            else { pendingClear = true; pendingApply = false; }
        }
    }

    // --- Faux tab creation ---
    private void addOrUpdateTab()
    {
        try
        {
            final Widget parent = client.getWidget(BANK_TAB_CONTAINER);
            if (parent == null) return;

            Widget w = (tabWidgetId != -1) ? client.getWidget(tabWidgetId) : null;
            if (w == null || w.isHidden())
            {
                w = parent.createChild(-1, WidgetType.TEXT);
                w.setText(config.tabName());
                w.setFontId(495);
                w.setTextColor(0xFFEEAA);
                w.setHasListener(true);
                w.setAction(0, "View");
                w.revalidate();
                tabWidgetId = w.getId();
            }

            // Position right-aligned within parent
            final int parentWidth = parent.getWidth();
            final int x = Math.max(0, parentWidth - 80);
            final int y = 6;
            w.setOriginalX(x);
            w.setOriginalY(y);
            w.setOriginalWidth(76);
            w.setOriginalHeight(16);
            w.setHidden(false);
            w.setTextColor(active ? 0x00FF00 : 0xFFEEAA);
            w.revalidate();
        }
        catch (Throwable t)
        {
            // fail silently to avoid impacting the client
        }
    }

    private void removeTab()
    {
        if (tabWidgetId != -1)
        {
            try
            {
                Widget w = client.getWidget(tabWidgetId);
                if (w != null) { w.setHidden(true); }
            } catch (Throwable ignored) {}
            tabWidgetId = -1;
        }
    }

    // --- Prefer BankSearch API to set text; reflect across versions ---
    private boolean setBankSearchText(String text)
    {
        final Class<?> cls = bankSearch.getClass();
        Method m;
        String[] names = new String[] { "setSearchText", "search", "updateSearch", "setText" };
        for (String name : names)
        {
            try
            {
                m = cls.getMethod(name, String.class);
                m.setAccessible(true);
                m.invoke(bankSearch, text);
                return true;
            }
            catch (Throwable ignored) {}
        }
        return false;
    }

    // ---- Reflection shims for BankSearch API variants ----
    private void registerBankPredicate(String token, Predicate<Integer> pred)
    {
        try
        {
            bankSearch.getClass()
                .getMethod("registerSearchPredicate", String.class, Predicate.class)
                .invoke(bankSearch, token, pred);
            return;
        }
        catch (NoSuchMethodException ignored) {}
        catch (Exception e) { /* ignore */ }

        try
        {
            bankSearch.getClass()
                .getMethod("registerSearch", String.class, Predicate.class)
                .invoke(bankSearch, token, pred);
        }
        catch (NoSuchMethodException ignored) {}
        catch (Exception e) { /* ignore */ }
    }

    private void unregisterBankPredicate(String token)
    {
        try
        {
            bankSearch.getClass()
                .getMethod("unregisterSearchPredicate", String.class)
                .invoke(bankSearch, token);
            return;
        }
        catch (NoSuchMethodException ignored) {}
        catch (Exception e) { /* ignore */ }

        try
        {
            bankSearch.getClass()
                .getMethod("unregisterSearch", String.class)
                .invoke(bankSearch, token);
        }
        catch (NoSuchMethodException ignored) {}
        catch (Exception e) { /* ignore */ }
    }
}
