# DB Scheduler UI

![build status](https://github.com/bekk/db-scheduler-ui/workflows/Build/badge.svg)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

A UI extension for [db-scheduler](https://github.com/kagkarlsson/db-scheduler) that provides a browser accessible
dashboard for monitoring and basic administration of tasks.

## Features

* **View tasks** that are Scheduled, Running or Failed.
* **Re-run or Run** your task directly from the User Interface
* **Delete tasks**
* **Uses SpringBoot** to launch a UI
* **View the history of all tasks** (optional)

<img alt="Screenshot" src="Screenshot_Frontpage.png" width=700/>
<img alt="Screenshot" src="Screenshot_Group.png" width=700/>
<img alt="Screenshot" src="Screenshot_History.png" width=700/>

## Table of contents

* [Getting started](#getting-started)
* [Using with Ktor (non-Spring applications)](#using-with-ktor-non-spring-applications)
* [SQL-задания (планировщик для баз данных)](#sql-задания-планировщик-для-баз-данных)
* [Пользователи и схемы (доступ к SQL-заданиям)](#пользователи-и-схемы-доступ-к-sql-заданиям)
* [Prerequisites](#prerequisites)
* [How it works](#how-it-works)
* [Configuration](#configuration)

### Prerequisites

* An existing Spring Boot application, with [db-scheduler](https://github.com/kagkarlsson/db-scheduler)
* Minimum db-scheduler version 16.12.0
* Minimum Java 17 and SpringBoot 3.4 (or SpringBoot 4.0 for the Spring Boot 4 starter)

## Getting started

1. Add the db-scheduler-ui spring boot starter maven dependency

   **For Spring Boot 3:**
   ```xml
   <dependency>
       <groupId>no.bekk.db-scheduler-ui</groupId>
       <artifactId>db-scheduler-ui-starter</artifactId>
       <version>4.6.0</version>
   </dependency>
   ```

   **For Spring Boot 4:**
   ```xml
   <dependency>
       <groupId>no.bekk.db-scheduler-ui</groupId>
       <artifactId>db-scheduler-ui-spring-boot-4-starter</artifactId>
       <version>4.6.0</version>
   </dependency>
   ```

2. Read the [db-scheduler](https://github.com/kagkarlsson/db-scheduler) readme and follow the getting started guide. The
   most important is to create the `scheduled_tasks` table correctly.
3. Start your application. The db-scheduler UI can be reached at `<your-app-url>/db-scheduler`

## Using with Ktor (non-Spring applications)

If you want to use db-scheduler-ui without Spring dependencies, you can use the Ktor plugin provided by [db-scheduler-additions](https://github.com/osoykan/db-scheduler-additions).

Add the dependency:

```kotlin
implementation("io.github.osoykan:db-scheduler-ui-ktor:$version")
```

Install the plugin in your Ktor application:

```kotlin
install(DbSchedulerUI) {
  routePath = "/db-scheduler"
  scheduler = { get<Scheduler>() } // provide your Scheduler instance
  enabled = true
  taskData = true
}
```

For more details and advanced configuration (including execution history support), see the [db-scheduler-additions repository](https://github.com/osoykan/db-scheduler-additions).

## Optional: task history

Task-history support is now built into the UI starter — no extra Maven dependency is required. The
execution-log writer is derived from
[`rocketbase-io/db-scheduler-log`](https://github.com/rocketbase-io/db-scheduler-log) by
Marten Prieß, vendored here with permission and under the Apache License 2.0. See [`NOTICE`](NOTICE)
for full attribution.

1. Create the `scheduled_execution_logs` table in your database. Pick the script for your engine from
   [`sql/log-table/`](sql/log-table):
   [postgresql](sql/log-table/postgresql.sql) ·
   [h2](sql/log-table/h2.sql) ·
   [oracle](sql/log-table/oracle.sql) ·
   [mssql](sql/log-table/mssql.sql) ·
   [mysql](sql/log-table/mysql.sql).

2. Enable history in your `application.properties`:

   ```properties
   # enable the UI-component for history
   db-scheduler-ui.history=true
   # enable the log-writer
   db-scheduler-ui.log.enabled=true
   ```

> **Migrating from `io.rocketbase.extension:db-scheduler-log-spring-boot-starter`:**
> remove that dependency and rename `db-scheduler-log.*` properties to `db-scheduler-ui.log.*`.

## SQL-задания (планировщик для баз данных)

Модуль `db-scheduler-ui-sql` позволяет создавать из интерфейса задания, которые подключаются
к другой базе данных и выполняют SQL-скрипт по cron-расписанию (или один раз). Целевые СУБД:
Oracle, Trino, Impala — и любая другая по JDBC. Хранилище планировщика и целевая база
разделены: задания живут в `scheduled_tasks` вашего приложения, скрипты выполняются в целевой базе.

1. Добавьте зависимость:

   ```xml
   <dependency>
     <groupId>no.bekk.db-scheduler-ui</groupId>
     <artifactId>db-scheduler-ui-sql</artifactId>
   </dependency>
   ```

2. Создайте таблицы `sql_job_connections` и `sql_job_executions` — скрипты для 5 диалектов в
   [`sql/sql-jobs/`](sql/sql-jobs):
   [postgresql](sql/sql-jobs/postgresql.sql) ·
   [h2](sql/sql-jobs/h2.sql) ·
   [oracle](sql/sql-jobs/oracle.sql) ·
   [mysql](sql/sql-jobs/mysql.sql) ·
   [mssql](sql/sql-jobs/mssql.sql).

3. Включите модуль и задайте ключ шифрования паролей соединений:

   ```properties
   db-scheduler-ui.sql=true
   # AES-GCM ключ (base64, 32 байта); без него пароли хранятся в открытом виде (с предупреждением)
   db-scheduler-ui.sql.encryption-key=${SQL_JOBS_ENCRYPTION_KEY}
   ```

4. **JDBC-драйвер целевой базы — «принесите сами»** (не входит в сборку из-за лицензии Oracle
   и веса hive-jdbc). Добавьте драйвер в classpath вашего приложения, например:

   ```xml
   <!-- Oracle -->
   <dependency>
     <groupId>com.oracle.database.jdbc</groupId>
     <artifactId>ojdbc11</artifactId>
     <version>23.x</version>
   </dependency>
   <!-- Trino -->
   <dependency>
     <groupId>io.trino</groupId>
     <artifactId>trino-jdbc</artifactId>
     <version>4xx</version>
   </dependency>
   <!-- Impala: Cloudera hive-jdbc — установите в локальный репозиторий вручную -->
   <!-- MySQL -->
   <dependency>
     <groupId>com.mysql</groupId>
     <artifactId>mysql-connector-j</artifactId>
     <version>9.x</version>
   </dependency>
   <!-- MariaDB -->
   <dependency>
     <groupId>org.mariadb.jdbc</groupId>
     <artifactId>mariadb-java-client</artifactId>
     <version>3.x</version>
   </dependency>
   <!-- PostgreSQL -->
   <dependency>
     <groupId>org.postgresql</groupId>
     <artifactId>postgresql</artifactId>
     <version>42.x</version>
   </dependency>
   <!-- MS SQL Server -->
   <dependency>
     <groupId>com.microsoft.sqlserver</groupId>
     <artifactId>mssql-jdbc</artifactId>
     <version>12.x</version>
   </dependency>
   <!-- ClickHouse (classifier all — со встроенным HTTP-клиентом) -->
   <dependency>
     <groupId>com.clickhouse</groupId>
     <artifactId>clickhouse-jdbc</artifactId>
     <version>0.8.x</version>
     <classifier>all</classifier>
   </dependency>
   ```

   Класс драйвера определяется по префиксу JDBC URL (`DriverManager`), указывать его не нужно.

5. В интерфейсе: вкладка **«Соединения»** → создать соединение (JDBC URL указывается целиком
   вручную; кнопка «Проверить» доступна у сохранённого соединения — через
   «Редактировать») → вкладка **«SQL-задания»** → создать задание (соединение, скрипты, cron
   с предпросмотром ближайших запусков, таймаут на оператор). История выполнений — со списком
   операторов, строками и ошибками.

Подробности и известные ограничения — в [`docs/АРХИТЕКТУРА.md`](docs/АРХИТЕКТУРА.md), раздел 8.

## Пользователи и схемы (доступ к SQL-заданиям)

По умолчанию интерфейс доступен всем, кому открыт его порт, а все SQL-задания и соединения
глобальны. Флаг `db-scheduler-ui.users.enabled=true` (требует `db-scheduler-ui.sql=true`)
включает собственную лёгкую систему доступа — без зависимости от Spring Security, поверх
безопасности host-приложения или вместо неё:

- **Пользователи** — своя таблица `sql_users`, форма логина, сессия в cookie.
- **Схемы** — именованные группы: внутри схемы лежат задания и соединения.
- **Гранты** — доступ выдаётся паре (пользователь × схема) и он **бинарный**: получен — внутри
  схемы можно всё (видеть, создавать, запускать, удалять); нет — схемы просто не существует
  (404, а не 403, чтобы не световать существование).
- **Админ** — глобальный флаг у пользователя: видит все схемы и управляет пользователями,
  схемами и грантами со страниц «Пользователи» / «Схемы».

```properties
db-scheduler-ui.sql=true
db-scheduler-ui.users.enabled=true
# Bootstrap-админ создаётся один раз при старте, только если таблица sql_users пуста.
# Смените пароль после первого входа — в properties он хранится открыто.
db-scheduler-ui.users.bootstrap-username=admin
db-scheduler-ui.users.bootstrap-password=смените-меня
```

Таблицы `sql_users`, `sql_schemas`, `sql_schema_grants` и колонка `schema_id` у соединений:

- свежая установка — основной DDL [`sql/sql-jobs/`](sql/sql-jobs) уже всё включает;
- существующая установка — `sql/sql-jobs/upgrade-users-<диалект>.sql` (postgresql, h2,
  oracle, mysql, mssql).

Задание обязано использовать соединение **из своей схемы** (проверяется на бэкенде, UI
фильтрует соединения по выбранной схеме). Существующие задания и соединения без схемы
видны только админу, пока он не назначит им схему.

Расписания и история тоже уважают гранты: SQL-задания недоступных схем вообще не
возвращаются API — фильтрация идёт на сервере. При нескольких доступных схемах на
вкладках «Scheduled» и «История» появляется фильтр «Схема»: «Все схемы» (по умолчанию —
видно всё, включая обычные задания хоста) или конкретная схема (тогда видны только
SQL-задания этой схемы). При ровно одной доступной схеме диалог выбора схемы прячется
везде — и в фильтрах, и в формах задания/соединения — и единственная схема подставляется
автоматически: у большинства пользователей ровно одна схема, и диалог их бы только
путал. Под это правило подпадает и админ, если схема в системе ровно одна.
Счётчики на вкладке «Overview» при этом по-прежнему считают все выполнения, включая
скрытые схемы.

SQL-задания на «Scheduled» подписываются своим именем, а не служебным `sql-script-task`
с UUID: бэкенд отдаёт имя и схему задания отдельным полем `displays`, поиск по имени
задачи находит задание и по нему. При двух и более доступных схемах между «Имя задачи»
и «Task-ID» появляется колонка «Схема»; у задания без схемы (легаси) и у обычных заданий
хоста в ней «—».

Что стоит знать про механизм:

- Все эндпоинты `/db-scheduler-api/**` закрыты фильтром сессий; публичные —
  `/config`, `/auth/login`, `/auth/me`, `/auth/logout`. Аноним получает 401.
- Мутации (POST/PUT/DELETE) требуют заголовок `X-Requested-With: XMLHttpRequest` —
  прагматичная CSRF-защита: межсайтовая форма не может послать кастомный заголовок.
  Cookie `HttpOnly`, id сессии ротируется при входе.
- Сессия перепроверяется по `sql_users` на каждом запросе: отключение, понижение или удаление
  пользователя действует немедленно, без перелогина.
- `db-scheduler-ui.read-only=true` перекрывает гранты: страницы управления пользователями,
  схемами и грантами исчезают целиком.
- Пароли — PBKDF2-HMAC-SHA256 (210k итераций, уникальная соль); в БД хранится только хэш.

Демо в `example-app`: `cd example-app && ../mvnw spring-boot:run -Dspring-boot.run.profiles=users`
(логин `admin`/`admin` — только для локального просмотра).

## How it works

db-scheduler-ui adds a REST-api package that has a bundled frontend application.
Springboot is used to configure beans and handle dependencies within the library and your application.
The user interface makes calls to the scheduler-client, where it can fetch, delete, run, and reschedule tasks.
These tasks are then shown in the web application.
An optional log module can also be added, making it possible to view the history of all your task executions.

## Configuration

db-scheduler-ui can be configured using the following options:

`history`: Turns on db-scheduler-log, default value is `false`. You can also limit the number of logs to fetch
with `log-limit`.

```
db-scheduler-ui.history=true
db-scheduler-ui.log-limit=1000
```

If you for some reason want to hide the task data you can set this to false. defaults to `true`

```
db-scheduler-ui.task-data=false
```

Or if you want a _read-only_ mode (in which tasks cannot be manually run, deleted or scheduled) set `read-only` to `true`, defaults to `false`

````
db-scheduler-ui.read-only=true
````

If you want to add a _prefix_ for the ui you can set the property below.

````
db-scheduler-ui.context-path=prefix
````

## Security

In case you want to secure db-scheduler-ui you can use [Spring Security](https://spring.io/projects/spring-security), you should secure the paths `/db-scheduler` and `/db-scheduler-api`.

In a more advanced scenario, you could assign an _admin role_ and a _read-only user_ role.
An example _filter chain_ with basic security:
```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
  return http
      .csrf(CsrfConfigurer::disable)
      .authorizeHttpRequests(
          authz ->
              authz
                  // protect the UI
                  .requestMatchers("/db-scheduler/**").hasAnyRole("ADMIN", "USER")
                  // allow read access to the API for both users and admins
                  .requestMatchers(HttpMethod.GET, "/db-scheduler-api/**").hasAnyRole("ADMIN", "USER")
                  // only admins can delete tasks, alter scheduling, ...
                  .requestMatchers(HttpMethod.POST, "/db-scheduler-api/**").hasAnyRole("ADMIN")
                  // other application specific security
                  .anyRequest().permitAll())
      .httpBasic(withDefaults())
      .build();
}
```
additionally you might want to hide delete, run, ... buttons in the UI. To achieve this, declare the following bean:
```java
@Bean
ConfigController configController(DbSchedulerUiProperties properties) {
  return new ConfigController(properties.isHistory(), properties.isOverview(), readOnly(properties));
}

private Supplier<Boolean> readOnly(DbSchedulerUiProperties properties) {
  // either global readonly mode is active or user has no admin rights  
  return () -> properties.isReadOnly() || !isAdmin();
}

private boolean isAdmin() {
  Authentication auth = SecurityContextHolder.getContext().getAuthentication();
  if (auth == null) {
    return false;
  }
  return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
}
```

## Contributing

Feel free to create pull requests if there are features or improvements you want to add.
PR's need to be approved by one of the maintainers. To publish a new version, create a release in Github and tag it with
a SemVer version.
A new release will then be released to maven central by a github action using JReleaser.

Before submitting a PR make sure to run `mvn spotless:apply` to format the code correctly.
Please use the prettier config when making frontend changes

## Local development

Prerequisites:

* Maven
* JDK17
* Node
* pnpm (`corepack enable`)

There are two ways to run the frontend locally.

1. running `pnpm run dev` inside the db-scheduler-ui-frontend folder
2. running `mvn install` will build the frontend and copy the output to the resources folder in the `db-scheduler-ui`
   module. The frontend will then be available at the same port as the example app.

To run the backend run `mvn clean install` and then run the example app.
