rem @ECHO OFF
if exist jdk ( 
    set javaDir=jdk\bin\
)
"%javaDir%java.exe" -Djava.security.manager -Dnxt.runtime.mode=desktop -Djava.security.policy=contractManager.policy -cp classes;lib\*;conf;addons\classes nxt.tools.ContractManager %*