FROM node:18-alpine

WORKDIR /app

COPY package*.json ./

RUN npm install express mysql2 pg cors dotenv

COPY middleware.js .

EXPOSE 4000

CMD ["node", "middleware.js"]