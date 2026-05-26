; NSIS installer script for CarneliaVPN
!include "MUI2.nsh"

Name "CarneliaVPN"
OutFile "CarneliaVPN-Installer-2.4.0.exe"
InstallDir "$PROGRAMFILES64\\CarneliaVPN"
RequestExecutionLevel user

Page components
Page directory
Page instfiles

Section "Main" SEC01
  SetOutPath "$INSTDIR"
  ; Files copied from the build directory. Ensure build artifacts are present.
  File "..\\build\\CarneliaVPN.exe"
  File "..\\build\\xray.exe"
  File "..\\build\\xray"
  ; Include data files if present
  IfFileExists "..\\xray-extracted\\geoip.dat" "File \"..\\xray-extracted\\geoip.dat\"" ""
  IfFileExists "..\\xray-extracted\\geosite.dat" "File \"..\\xray-extracted\\geosite.dat\"" ""

  CreateDirectory "$SMPROGRAMS\\CarneliaVPN"
  CreateShortCut "$SMPROGRAMS\\CarneliaVPN\\CarneliaVPN.lnk" "$INSTDIR\\CarneliaVPN.exe"
SectionEnd

Section "Uninstall"
  Delete "$INSTDIR\\CarneliaVPN.exe"
  Delete "$SMPROGRAMS\\CarneliaVPN\\CarneliaVPN.lnk"
  RMDir "$SMPROGRAMS\\CarneliaVPN"
  RMDir "$INSTDIR"
SectionEnd
