import http from 'k6/http';
import { check } from 'k6';

export const options = {
    thresholds: {
        checks: ['rate==1'],
    },
};

export default function () {

    const response = http.get(
        'http://host.docker.internal:8080/v1/quotes/PETR4'
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
    });

    console.log(`status=${response.status}`);
    console.log(`body=${response.body}`);

}