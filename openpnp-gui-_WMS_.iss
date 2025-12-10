; WMS_OpenPnP Installer Script with bundled JRE

[Setup]
AppName=WMS_OpenPnP
AppVersion=1.0
DefaultDirName={pf}\WMS_OpenPnP
DefaultGroupName=WMS_OpenPnP
OutputBaseFilename=WMS_OpenPnP_Setup
Compression=lzma
SolidCompression=yes

[Files]
; Copy your forked JAR
Source: "C:\path\to\openpnp-gui-1.0.jar"; DestDir: "{app}"; Flags: ignoreversion

; Copy the JRE folder (downloaded or prepared separately)
Source: "C:\path\to\jre17\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

; Copy the batch launcher
Source: "C:\path\to\run-openpnp.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\WMS_OpenPnP"; Filename: "{app}\run-openpnp.bat"
Name: "{commondesktop}\WMS_OpenPnP"; Filename: "{app}\run-openpnp.bat"

[Run]
Filename: "{app}\run-openpnp.bat"; Description: "Launch WMS_OpenPnP"; Flags: nowait postinstall skipifsilent
