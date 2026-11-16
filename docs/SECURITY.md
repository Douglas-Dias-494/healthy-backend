# Segurança do Healthy

O sistema guarda dados de saúde, que a LGPD classifica como **dados pessoais sensíveis**. Este documento lista o que
já está protegido, o que depende de configuração em produção e o que ainda é risco conhecido.

## O que já está implementado

**Autenticação e sessão**
- Senhas guardadas com **BCrypt** (hash lento e com sal). A senha em si nunca é gravada nem registrada em log.
- **Política de senha** no servidor: 8 a 72 caracteres, ao menos uma letra e um número, fora de uma lista de senhas comuns.
- Login com **mensagem única** para e-mail inexistente, senha errada ou perfil errado, e com tempo de resposta
  equalizado (compara sempre contra um hash), para não revelar quais e-mails têm conta.
- **JWT** assinado (HS256) com emissor, validade (8 h) e **versão**. Trocar ou redefinir a senha soma 1 à versão
  do usuário e **encerra todas as sessões antigas**.
- Rotas públicas ignoram o cabeçalho `Authorization` (um token vencido não impede de entrar de novo).

**Proteção contra abuso**
- **Limite de tentativas** (janela deslizante): 5 falhas de login por e-mail e 20 por IP a cada 15 min (também
  barra quem tem a senha certa depois de errar demais); 10 cadastros por hora por IP; 5 pedidos de recuperação por
  IP e 3 por e-mail por hora; 10 tentativas de link inválido por hora por IP; 5 tentativas de trocar a senha;
  30 buscas de paciente por minuto por médico. Resposta `429` com `Retry-After`.
- A busca de paciente por e-mail devolve o mínimo (id e nome) e responde igual para "não existe" e "é um médico".

**Recuperação de senha**
- Token aleatório de 256 bits, **só o hash (SHA-256) fica no banco**, vale 30 min e é de **uso único**; um pedido
  novo invalida o anterior. A resposta de "esqueci minha senha" é sempre a mesma, exista a conta ou não.
- O e-mail é enviado em segundo plano (não revela a existência da conta pelo tempo de resposta).

**Autorização**
- Cada rota exige o perfil certo (`/api/pacientes/**` só paciente, `/api/medicos/**` só médico).
- Os dados são sempre filtrados **pelo dono do token**; id de outra pessoa responde 404 (e não 403), para não
  revelar que o id existe. O médico só emite receita para quem atendeu. A dosagem de um acompanhamento vem da
  receita, nunca do cliente.
- CPF só é devolvido ao próprio paciente e nunca aparece em telas ou respostas do médico.

**Cabeçalhos e rede**
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `Cache-Control: no-store`
  e `Strict-Transport-Security` (enviado quando a conexão é HTTPS).
- CORS restrito às origens configuradas (sem curinga). Sem sessão em cookie, então não há CSRF a mitigar.
- Respostas de erro sem detalhes internos (`server.error.include-*=never`).

**Configuração e operação**
- Perfis `dev` e `prod`. Em `prod`, a aplicação **recusa subir** se faltar variável obrigatória, se usar o segredo
  de desenvolvimento, ou se o console do H2 ou os dados de teste estiverem ligados.
- Eventos de segurança no logger `security` (login, falhas, bloqueios, trocas de senha), com e-mail mascarado e
  sem segredos.
- Migrações de banco versionadas (Flyway). CI e Dependabot configurados.

## Checklist para colocar no ar

- [ ] HTTPS obrigatório, terminado num proxy reverso (nginx, Caddy, load balancer). O Angular de produção chama
      `/api` no mesmo endereço do site, e o proxy encaminha `/api` para o Spring (`server.forward-headers-strategy=framework`).
- [ ] Definir `HEALTHY_JWT_SECRET` (e guardar em um gerenciador de segredos, não no repositório).
- [ ] Configurar o SMTP e testar o fluxo "esqueci minha senha" de ponta a ponta.
- [ ] Trocar o H2 por um banco servidor (PostgreSQL) e definir `HEALTHY_DATABASE_*`. O H2 em arquivo é de **um** processo só.
- [ ] Backups do banco e teste de restauração.
- [ ] Enviar o logger `security` para um local centralizado e criar alertas (muitos `RATE_LIMITED`/`LOGIN_FAILURE`).
- [ ] Revisar a política de privacidade, base legal e consentimento (LGPD) antes de receber dados reais.

## Riscos conhecidos (próximos passos)

| Risco | Situação | Como reduzir |
|---|---|---|
| **XSS roubar o token** | O token fica em `localStorage`/`sessionStorage` (necessário para o Angular enviá-lo). Um script injetado conseguiria lê-lo. | Angular já escapa o conteúdo; manter dependências atualizadas; definir uma CSP rígida no servidor que entrega o site; no futuro, cookie `HttpOnly` + `SameSite` (exige CSRF). Sessão de 8 h e invalidação na troca de senha reduzem o dano. |
| **Limite de tentativas em memória** | Cada instância conta por conta própria e o estado some ao reiniciar. | Com mais de um servidor, mover o contador para o Redis (ou usar o limitador do proxy). |
| **Sem segundo fator (2FA)** | Quem descobre a senha entra. | TOTP para médicos (têm acesso a mais dados) e opcional para pacientes. |
| **E-mail não é verificado no cadastro** | Alguém pode cadastrar um e-mail que não é seu. | Link de confirmação antes de ativar a conta. |
| **Cadastro revela e-mail já usado (409)** | Permite descobrir quem tem conta (mitigado pelo limite de cadastros). | Responder igual e avisar o dono por e-mail; custa uma pior experiência. |
| **CRM não é validado** | Qualquer pessoa pode se cadastrar como médico. | Validar no CFM/CRM e aprovar manualmente antes de liberar a conta de médico. |
| **Dados em repouso sem criptografia** | CPF e demais dados ficam em texto no banco. | Criptografia de disco/banco e, para o CPF, criptografia por coluna. |
| **Sem trilha de auditoria de acesso a dados clínicos** | Só há log de eventos de autenticação. | Registrar quem leu/alterou receitas e fichas (exigência prática da LGPD). |
| **Direitos do titular (LGPD)** | Não há exportar nem excluir a conta. | Endpoints de exportação e exclusão/anonimização, com política de retenção. |
| **Senhas vazadas** | A checagem é contra uma lista pequena e local. | Consultar a API "Have I Been Pwned" (k-anonymity) ao definir a senha. |

## Como relatar uma falha

Não abra uma issue pública com detalhes exploráveis: avise o responsável pelo repositório em particular.
