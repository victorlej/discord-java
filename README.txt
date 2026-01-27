Instruction pour compiler et lancer le Discord Java:

NOUVEAU - STRUCTURE STANDARDISÉE
--------------------------------
J'ai réorganisé le projet pour suivre le standard Java (Maven), ce qui corrige les erreurs dans VS Code.

Les fichiers sont maintenant dans : src/main/java/

1. Compiler (depuis la racine discord-java):
   javac src/main/java/common/*.java src/main/java/server/*.java src/main/java/client/*.java

   OU (recommandé si vous avez Maven):
   mvn clean install

2. Lancer le Serveur:
   java -cp src/main/java server.Server

3. Lancer le Client:
   java -cp src/main/java client.Main

NB: Dans VS Code, vous pouvez maintenant simplement cliquer sur le bouton "Lecture" au-dessus des classes Main, car la structure est standard.
