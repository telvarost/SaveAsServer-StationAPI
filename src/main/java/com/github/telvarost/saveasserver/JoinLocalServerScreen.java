package com.github.telvarost.saveasserver;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Environment(EnvType.CLIENT)
public class JoinLocalServerScreen extends Screen {
    private Screen parent;

    private int progress = 0;
    private int currentTick = 0;

    private List<String> fileList;
    private int fileIndex = 0;

    public JoinLocalServerScreen(Screen parent) {
        this.parent = parent;
    }

    public void zipIt(String zipFile) {
        File savesDir = new File(Minecraft.getRunDirectory(), "saves");
        File worldDir = new File(savesDir, ModHelper.ModHelperFields.CurrentWorldFolder);
        byte[] buffer = new byte[1024];
        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        try {
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);

            System.out.println("Output World to Zip : " + zipFile);
            FileInputStream in = null;

            int fileListLength = (null != fileList) ? fileList.size() : 0;
            for (fileIndex = 0; fileIndex < fileListLength; fileIndex++) {
                System.out.println("Zipping : " + fileList.get(fileIndex));
                ZipEntry ze = new ZipEntry(fileList.get(fileIndex));
                zos.putNextEntry(ze);
                try {
                    in = new FileInputStream(worldDir.getAbsolutePath().replaceAll("\\\\", "/") + File.separator + fileList.get(fileIndex));
                    int len;
                    while ((len = in .read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                } finally {
                    in.close();
                }
            }

            zos.closeEntry();
            System.out.println("World backup successfully created!");

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                zos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void generateFileList(File node) {
        // add file only
        if (node.isFile()) {
            fileList.add(generateZipEntry(node.getAbsolutePath().replaceAll("\\\\", "/")));
        }

        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename: subNote) {
                generateFileList(new File(node, filename));
            }
        }
    }

    private String generateZipEntry(String file) {
        File savesDir = new File(Minecraft.getRunDirectory(), "saves");
        File worldDir = new File(savesDir, ModHelper.ModHelperFields.CurrentWorldFolder);
        return file.substring(worldDir.getAbsolutePath().replaceAll("\\\\", "/").length() + 1, file.length());
    }

    public void init() {
        ModHelper.ModHelperFields.LaunchingLocalServer = false;
        this.buttons.clear();
        this.currentTick = 0;

        if (false == ModHelper.ModHelperFields.IsWorldBackupStarted) {
            ModHelper.ModHelperFields.IsWorldBackupStarted = true;

            /** - Decide whether to back up world save before server launch based on config */
            if (Config.config.BACKUP_WORLD_ON_LAN_SERVER_LAUNCH) {

                /** - Prepare loading bar */
                this.minecraft.progressRenderer.progressStart("Opening World to LAN...");
                this.minecraft.progressRenderer.progressStartNoAbort("Opening World to LAN...");
                this.minecraft.progressRenderer.progressStage("Creating world backup file");
                this.minecraft.progressRenderer.progressStagePercentage(0);

                ModHelper.ModHelperFields.IsZipInProgress = false;
            } else {

                /** - Prepare loading bar */
                this.minecraft.progressRenderer.progressStart("Opening World to LAN...");
                this.minecraft.progressRenderer.progressStartNoAbort("Opening World to LAN...");
                this.minecraft.progressRenderer.progressStage("Preparing world");
                this.minecraft.progressRenderer.progressStagePercentage(0);

                ModHelper.ModHelperFields.IsServerLaunched = false;
            }
        }
    }

    public void tick() {
        currentTick++;

        /** - Check to create world backup file and server lock */
        if (false == ModHelper.ModHelperFields.IsZipInProgress) {
            ModHelper.ModHelperFields.IsZipInProgress = true;

            /** - Prepare and zip world files */
            fileList = new ArrayList<String>();
            File savesDir = new File(Minecraft.getRunDirectory(), "saves");
            File worldDir = new File(savesDir, ModHelper.ModHelperFields.CurrentWorldFolder);
            generateFileList(worldDir);
            zipIt("saves" + File.separator + "_" + ModHelper.ModHelperFields.CurrentWorldFolder + ".zip");

            /** - Set flag letting server know that it can now launch */
            ModHelper.ModHelperFields.IsServerLaunched = false;
        }

        /** - Check to launch server */
        if (false == ModHelper.ModHelperFields.IsServerLaunched) {
            ModHelper.ModHelperFields.IsServerLaunched = true;

            /** - Create server lock */
            File savesDir = new File(Minecraft.getRunDirectory(), "saves");
            File worldDir = new File(savesDir, ModHelper.ModHelperFields.CurrentWorldFolder);
            File serverLock = new File(worldDir, "server.lock");
            if (!serverLock.exists()) {
                try {
                    serverLock.createNewFile();
                } catch (IOException e) {
                    System.out.println("Failed to create server lock file! Client player may be de-synced after launch!");
                }
            }

            /** - Prepare logging folder */
            File[] files = new File("logging").listFiles();
            for(File currentFile : files){
                currentFile.delete();
            }

            /** - Update loading bar */
            this.minecraft.progressRenderer.progressStage("Preparing world");
            this.minecraft.progressRenderer.progressStagePercentage(0);

            /** - Launch server */
            String argNoGui = (Config.config.SERVER_GUI_ENABLED) ? "" : "nogui";
            ProcessBuilder pb = new ProcessBuilder(Config.config.JAVA_PATH, "-jar", "local-babric-server.0.16.9.jar", argNoGui);
            pb.directory(Minecraft.getRunDirectory());
            try {
                ModHelper.ModHelperFields.CurrentServer = pb.start();
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    public void run() {
                        if (null != ModHelper.ModHelperFields.CurrentServer) {
                            Minecraft client = (Minecraft)FabricLoader.getInstance().getGameInstance();
                            if (null != client) {
                                client.setScreen(new TitleScreen());
                            }
                        }
                    }
                }, "ServerCrashMonitor-thread"));
            } catch (IOException ex) {
                this.minecraft.setScreen(new TitleScreen());
                System.out.println("Failed to open client world to LAN: " + ex.toString());
            }
        }

        /** - Check for server loading start file */
        if (2 == currentTick) {
            File saveAsServerBegin = new File("logging" + File.separator + "preparing-level");
            if (saveAsServerBegin.exists()) {
                ModHelper.ModHelperFields.IsPreparationStarted = true;
                saveAsServerBegin.delete();

                System.out.println("Preparing LAN server...");
            }
        }

        /** - Check for server loading files */
        if (  (ModHelper.ModHelperFields.IsPreparationStarted)
           && (4 == currentTick)
        ) {
            File[] files = new File("logging").listFiles();
            String searchLevel = "loading-level";
            String searchProgress = "level-progress";
            for(File currentFile : files){
                String fileName = currentFile.getName();

                if(fileName.toLowerCase().indexOf(searchLevel.toLowerCase()) != -1)
                {
                    String levelString = fileName.substring("loading-level-".length());
                    this.minecraft.progressRenderer.progressStage("Preparing start region for level " + levelString);
                    progress = 0;
                    currentFile.delete();
                }

                if(fileName.toLowerCase().indexOf(searchProgress.toLowerCase()) != -1)
                {
                    String progressString = fileName.substring("level-progress-".length());
                    progress = parseInt(progressString, progress);
                    currentFile.delete();
                }
            }
        }

        /** - Check for server loading finished file */
        if (5 < currentTick) {
            currentTick = 0;
            File saveAsServerEnd = new File("logging" + File.separator + "done-loading");
            if (saveAsServerEnd.exists()) {
                progress = 100;
                saveAsServerEnd.delete();

                /** - Have the client join the local server */
                System.out.println("Done loading LAN server!");
                joinLocalServer();
            }
        }
    }

    private void joinLocalServer() {
        String var2 = "127.0.0.1:" + Config.config.SERVER_PORT;
        this.minecraft.options.lastServer = var2.replaceAll(":", "_");
        this.minecraft.options.save();
        String[] var3 = var2.split(":");
        if (var2.startsWith("[")) {
            int var4 = var2.indexOf("]");
            if (var4 > 0) {
                String var5 = var2.substring(1, var4);
                String var6 = var2.substring(var4 + 1).trim();
                if (var6.startsWith(":") && var6.length() > 0) {
                    var6 = var6.substring(1);
                    var3 = new String[]{var5, var6};
                } else {
                    var3 = new String[]{var5};
                }
            }
        }

        if (var3.length > 2) {
            var3 = new String[]{var2};
        }

        this.minecraft.setScreen(new ConnectScreen(this.minecraft, var3[0], var3.length > 1 ? this.parseInt(var3[1], 25565) : 25565));
    }

    private int parseInt(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception var4) {
            return defaultValue;
        }
    }

    public void removed() {
        /** - Do nothing */
    }

    protected void buttonClicked(ButtonWidget button) {
        /** - Do nothing */
    }

    protected void keyPressed(char character, int keyCode) {
        /** - Do nothing */
    }

    protected void mouseClicked(int mouseX, int mouseY, int button) {
        /** - Do nothing */
    }

    public void render(int mouseX, int mouseY, float delta) {
        this.minecraft.progressRenderer.progressStagePercentage(progress);
    }
}
