# Projeto do TG-bot Editor

Este projeto foi criado pelo TG-bot Editor e pode ser compilado e executado sem
o aplicativo desktop. `bot.json` contém os fluxos, `scripts/` contém as classes
Java editáveis, e `runtime/` contém o runner de terminal. Nenhum WebView ou
JavaFX é necessário para executar o bot.

## Build e execução

Use OpenJDK 25 com `javac` e o Gradle Wrapper incluído. Na pasta do projeto:

```sh
./gradlew clean build
java -jar build/libs/*.jar --check bot.json
```

`--check` valida os gatilhos e compila todas as ações sem conectar ao Telegram
ou exigir um token. Para executar o bot, copie `.env.example` para `.env`,
preencha `TG_BOT_TOKEN` com o token do seu bot e rode:

```sh
java -jar build/libs/*.jar bot.json
```

O arquivo `.env`, a pasta `data/` e o build `build/` são locais e ignorados
pelo Git. Variáveis do processo têm prioridade sobre as de `.env`. Use apenas
uma instância por token para evitar disputas no long polling.
O bot precisa ter permissão de publicar nos canais configurados. O runner
mostra a descrição de erros da API do Telegram e encerra o polling quando
recebe um HTTP 4xx permanente, evitando tentativas repetidas sem fim.

## Termux

Com o Git e o OpenJDK 25 já configurados, clone o repositório na pasta privada
do Termux (`$HOME`), execute o Gradle Wrapper incluído:

```sh
cd "$HOME"
git clone URL-DO-SEU-REPOSITORIO
cd NOME-DA-PASTA-CLONADA
java -version
javac -version
./gradlew clean build
java -jar build/libs/*.jar --check bot.json
cp .env.example .env
chmod 600 .env
# Edite .env com seu editor de preferência antes de iniciar.
java -jar build/libs/*.jar bot.json
```

O wrapper baixa o Gradle na primeira execução. Use `./gradlew --version` para
verificar o JDK. O alvo do projeto é Java 21; o JDK 25 o compila e executa.
