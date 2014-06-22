@echo Off

set JAVA_HOME="E:\Apps\Java\jdk1.7.0_25(32)"
set JAVA_HOME="E:\Apps\Java\jdk1.7.0_25"
set JAVA_HOME="E:\Apps\Java\jdk1.6.0_23"
set JAVA_HOME=E:/Apps/Java/jdk1.6.0_23



rem SET path=%path%;%JAVA_HOME%;E:\Android\adt-bundle-windows-x86_64-20130917\sdk\tools
SET path=E:\Apps\Java\jdk1.6.0_23\bin;E:\Android\adt-bundle-windows-x86_64-20130917\sdk\tools;E:\Apps\apache-ant-1.9.4\bin

rem "E:\Android\adt-bundle-windows-x86_64-20130917\sdk\tools\android.bat" update project --path .

rem java -version

ant release




