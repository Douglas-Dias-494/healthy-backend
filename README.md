# Healthy API

API do Healthy (Spring Boot 4, Java 21, H2). Conecta pacientes e médicos: contas, consultas, receitas digitais e
acompanhamento de medicamentos. O frontend Angular fica no repositório `healthy-frontend`.

## Rodar em desenvolvimento

```bash
./mvnw spring-boot:run
```

- API em `http://localhost:8080` (perfil `dev` é o padrão).
- Banco H2 em arquivo (`./data/healthydb`) e console em `http://localhost:8080/h2-console`.
- Na primeira execução cria usuários, consultas e receitas de teste (senha `123456`):
  `paciente@healthy.com`, `jose.almeida@healthy.com`, `paula.reis@healthy.com` (pacientes) e
  `medico@healthy.com`, `ana.souza@healthy.com`, `carlos.lima@healthy.com` (médicos).
- Sem servidor de e-mail: o link de "esqueci minha senha" aparece **no log da API**.

## Testes

```bash
./mvnw test
```

Os testes de integração sobem a aplicação inteira com banco em memória (perfil `test`), aplicam as migrações
de verdade e chamam a API como o Angular chamaria.

## Produção (perfil `prod`)

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/healthy-api-0.0.1-SNAPSHOT.jar
```

Se alguma variável obrigatória faltar, a aplicação **não sobe** (de propósito).

| Variável | Obrigatória | Para quê |
|---|---|---|
| `HEALTHY_JWT_SECRET` | sim | Segredo de assinatura dos tokens. Longo e aleatório (`openssl rand -base64 48`). Trocá-lo encerra todas as sessões. |
| `HEALTHY_CORS_ORIGINS` | sim | Endereço(s) do site, separados por vírgula. Sem curinga. |
| `HEALTHY_FRONTEND_URL` | sim | Endereço do site (links do e-mail de recuperação de senha). |
| `HEALTHY_MAIL_FROM` | sim | Remetente dos e-mails. |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | sim | Servidor SMTP. |
| `HEALTHY_DATABASE_URL`, `HEALTHY_DATABASE_USER`, `HEALTHY_DATABASE_PASSWORD` | não | Padrão: H2 em arquivo `./data/healthydb`. |

Em produção: console do H2 e dados de teste desligados (a aplicação recusa subir se estiverem ligados), e o
servidor deve ficar atrás de um proxy reverso com **HTTPS** (ver `docs/SECURITY.md`).

## Banco de dados e migrações

O esquema é gerenciado pelo **Flyway** (`src/main/resources/db/migration`). O Hibernate só confere
(`ddl-auto=validate`) se as entidades batem com o banco. Para alterar o banco:

1. crie um arquivo novo, ex.: `V3__descricao_curta.sql` (nunca edite uma migração já aplicada);
2. ajuste a `@Entity` correspondente;
3. rode `./mvnw test`: o teste sobe o banco com todas as migrações e valida as entidades.

Bancos criados antes do Flyway são tratados como já estando na versão 1 (`baseline-on-migrate`).

## Estrutura

```
com.healthy
├── auth/          login, cadastro, recuperação e troca de senha, JWT
├── usuario/       conta de acesso (Usuario, Perfil)
├── patient/       ficha do paciente (CPF, nascimento) e área do paciente
├── doctor/        área do médico (pacientes, atendimentos, receitas)
├── appointment/   consultas
├── prescription/  receitas
├── tracking/      acompanhamento de medicamentos
├── security/      limite de tentativas, auditoria, validação do token
├── common/        exceções e validações compartilhadas
└── config/        segurança, CORS, tratamento de erros, dados de teste
```

Documentação dos endpoints: `healthy-frontend/docs/API-ENDPOINTS.md`. Segurança: `docs/SECURITY.md`.
