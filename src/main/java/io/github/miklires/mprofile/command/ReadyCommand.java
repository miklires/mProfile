package io.github.miklires.mprofile.command;

import io.github.miklires.mprofile.MProfilePlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public final class ReadyCommand implements BasicCommand {
    private final MProfilePlugin plugin;
    private final Supplier<BasicCommand> delegate;

    public ReadyCommand(MProfilePlugin plugin, Supplier<BasicCommand> delegate) {
        this.plugin = plugin;
        this.delegate = delegate;
    }

    @Override public void execute(@NotNull CommandSourceStack source, @NotNull String[] args) {
        BasicCommand command = delegate.get();
        if (command == null) plugin.messages().send(source.getSender(), "loading");
        else command.execute(source, args);
    }

    @Override public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, @NotNull String[] args) {
        BasicCommand command = delegate.get();
        return command == null ? List.of() : command.suggest(source, args);
    }
}
