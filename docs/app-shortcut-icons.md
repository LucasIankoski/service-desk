# Ícone do atalho no celular

A aplicação usa automaticamente a logo cadastrada em **Logotipo da escola** como ícone do atalho. Logos já cadastradas também são aproveitadas, sem novo envio. Sem logo, é exibido um símbolo de escola.

São gerados PNGs de 32, 180, 192 e 512 pixels, com fundo branco e margem para os recortes dos sistemas operacionais. A imagem mantém a proporção original. O manifesto informa o nome da instituição e usa a página inicial como destino do atalho.

## Validação em dispositivos

1. Acesse a aplicação publicada por HTTPS em um Android e em um iPhone.
2. No Chrome do Android e no Safari do iPhone, adicione a aplicação à tela inicial.
3. Confirme que o ícone contém a logo e que o atalho abre a aplicação.
4. Troque a logo nas configurações e crie um novo atalho para conferir a nova imagem.
5. Remova a logo e confirme que um novo atalho usa o símbolo padrão.
6. Repita a criação a partir da tela de login, sem sessão autenticada.

Atalhos existentes podem manter o ícone anterior no cache do sistema. Nesse caso, remova e crie o atalho novamente. Logos com pouco texto tendem a ser mais legíveis. A mudança não adiciona funcionamento offline.

## Verificação automatizada

No backend: `./mvnw -Dtest=AppIconTests,SchoolLogoTests test` (Java 25).
No frontend: `npm run build`.

Os testes verificam acesso público, tamanhos, proporção, transparência, substituição e remoção da logo, atualização das URLs do manifesto e rejeição de tamanhos não suportados.
