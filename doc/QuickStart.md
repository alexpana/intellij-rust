**Bold text** denotes the names of actions you can type in the **Find Action**
  dialog (`Ctrl+Shift+A`).

After you have installed the plugin you can **open** a project. This can be done from the welckome screen in any IDE:

![idea-open](https://cloud.githubusercontent.com/assets/1711539/14491299/9e663e30-0180-11e6-9744-def829cbe329.png)

![py-open](https://cloud.githubusercontent.com/assets/1711539/14491095/85f23ea4-017f-11e6-9809-fb4c7cbb248e.png)

Select the directory with the project.

![select-directory](https://cloud.githubusercontent.com/assets/1711539/14491098/85f750f6-017f-11e6-81ec-f1fcab920c8f.png)

With IntelliJ IDEA specifically you can also **import project** from sources.

![idea-import](https://cloud.githubusercontent.com/assets/1711539/14211294/e0ce72c8-f835-11e5-9bfd-061098d70243.png)

![from-sources](https://cloud.githubusercontent.com/assets/1711539/14491096/85f346f0-017f-11e6-8f68-138a65d2cfb9.png)

Next you should tell the IDE which version of Cargo it should use. Open
**settings** (`Ctrl+Alt+S`) and find *Cargo* tab under *Languages & Frameworks*.
Here you can specify location for Rust tools (it should be deduced automatically
for conventional setup). If the location is valid, Rust version will be
displayed. Plugin automatically watches `Cargo.toml` for changes and
communicates with Cargo to learn about project structure and dependencies. If
you disable this behavior, or if you want to force a project update, you can use
**Refresh Cargo project** action. Don't forget to click **Apply** to actually
save the settings.

![settings](https://cloud.githubusercontent.com/assets/1711539/14491097/85f717d0-017f-11e6-98d6-0f60ee0e2016.png)

Wait until Cargo downloads all project dependencies. To check that everything is
working, try **Goto Symbol** (`Ctrl+Alt+Shift+N`) and type something. Note that
dependencies are present under external libraries. **Goto Symbol** should also
work for items from the external crates.

![go-to-symbol](https://cloud.githubusercontent.com/assets/1711539/14491412/44200bd0-0181-11e6-9587-10e4a07fa961.png)

To execute Cargo tasks from within the IDE, you need to set up a [Run
Configuration](https://www.jetbrains.com/idea/help/creating-and-editing-run-debug-configurations.html).
**Edit configurations** (`Alt+Shift+F10`) and add a "Cargo command" config. Be
sure to click the green plus sign, and **not** the "Defaults" :) You can also
use `Alt+Insert` shortcut here.

![add-run-configuration](https://cloud.githubusercontent.com/assets/1711539/14211919/33d29e60-f839-11e5-8c08-c8d09cbbf4ee.png)

Fill in the name of a command and additional arguments.

![edit-run-configuration](https://cloud.githubusercontent.com/assets/1711539/14211918/33ce8e56-f839-11e5-92c2-8c96bf365699.png)

You should be able to compile and **Run** (`Shift+f10`) your application from the IDE now:

![running](https://cloud.githubusercontent.com/assets/1711539/14211917/33cb0c54-f839-11e5-8026-d4fd7a7b44fd.png)

# Updating

In general updating the plugin should just work even if we change things we
cache or persist. However given the current rate of change we do not test for
this and so it is possible for data from the previous versions to confuse the
newer version of the plugin. You can **Refresh Cargo project** to force a
reimport of Cargo project. You can use **Invalidata caches/Restart** to rebuild
indices.

# Tips

Check out [tips](Tips.md) for some neat tricks!