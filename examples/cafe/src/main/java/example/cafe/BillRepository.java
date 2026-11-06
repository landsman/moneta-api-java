package example.cafe;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

interface BillRepository extends ListCrudRepository<Bill, Long> {

    List<Bill> findByPaidByIsNullOrderById();

    List<Bill> findAllByOrderByIdDesc();
}
