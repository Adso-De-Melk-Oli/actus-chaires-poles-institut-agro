# Actus | Pôles & Chaires

**Le site : https://adso-de-melk-oli.github.io/actus-chaires-poles-institut-agro/**

Les actualités et l'agenda des pôles et chaires de l'Institut Agro à Montpellier, réunis en un seul fil, avec un filtre par pôle ou chaire (ou « Pôles » / « Chaires ») et par type. Site jumeau de [Actus | L'Institut Agro](https://github.com/Adso-De-Melk-Oli/actus-institut-agro) : même fonctionnement, même format de données (`data/articles.json`), pour l'application Android à venir.

## Comment ça marche

- `index.html` : le site. Il lit `data/articles.json` ; le bouton Réactualiser recharge ce fichier.
- `scripts/collect.mjs` : le robot de collecte. Il lit les flux RSS ou l'API WordPress quand ils existent, sinon les pages Actualités et Agenda, et respecte le `robots.txt` de chaque site. Quand un flux mêle actus et événements, un billet titré ou classé comme un événement (colloque, webinaire, journée…) et daté va dans l'agenda ; sinon il reste une actu.
- `.github/workflows/collect.yml` : relève les sites du lundi au vendredi (plusieurs horaires GitHub, le premier qui passe fait le travail), enregistre les nouveautés puis envoie le digest à partir de 7 h. Lancement à la main : onglet **Actions** → **Relevé des actus** → **Run workflow**. Une modification des règles de lecture poussée sur `main` lance aussi un relevé.

| Source | Méthode |
|---|---|
| Pôle Vigne et Vin | Page Actualités ; page Agenda (une puce par événement) |
| Pôle Alimentation Durable | Page Actualités (pas d'agenda en ligne) |
| Chaire partenariale AgroSYS | Flux RSS (actus et « Evénements AgroSYS ») |
| Chaire AgroTIC | API WordPress : catégories « Les actualités » et « Évènements » (offres d'emploi exclues) |
| Mas numérique | Flux RSS |
| Chaire Innov'Arbo | Flux RSS de la catégorie de la chaire sur le site de la Fondation |
| Chaire UNESCO Alimentations du monde | Pages Actualités (sans dates) et Agenda |
| Chaire Eau, Agriculture et Changement Climatique | Flux RSS (l'API du site est fermée) |
| Chaire d'entreprises Vigne et Vin | Flux RSS (la page Actualités refuse les robots) |

## Digest du matin

Chaque jour ouvré, à partir de 7 h, un digest part **uniquement à olivier.piau@supagro.fr** (adresse fixée dans `scripts/send.mjs`) : nouveautés depuis le précédent envoi rangées par pôle et chaire, événements du jour et des 7 prochains jours. Envoi par Gmail, photos jointes ; secrets `GMAIL_USER` et `GMAIL_APP_PASSWORD` (Settings → Secrets and variables → Actions).

- **`data/sent.json`** garde le dernier envoi : le digest suivant reprend là où il s'arrête.
- **Alerte** quand une source est en échec deux jours de relevé de suite, puis un rappel par semaine.
- **Nouvelle source** : son historique rejoint le site sans être annoncé comme nouveau.
- **Essai** : Actions → « Envoi du digest (essai) » → Run workflow.

Si une source échoue, ses contenus de la veille sont conservés et l'encadré « Sources suivies » le signale. Les actus de plus de 12 mois et les événements terminés depuis plus de 6 mois sont retirés, mais les 6 actus les plus récentes de chaque source restent toujours (certaines chaires publient peu).

## Lancer en local

```sh
npm install
npm test          # lecture des dates
npm run collect   # met à jour data/articles.json
npx serve .       # puis ouvrir http://localhost:3000
```
